/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier.Recognition;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class MultiBoxTracker {
  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 16.0f;
  private static final int[] COLORS = {
    Color.BLUE,
    Color.RED,
    Color.GREEN,
    Color.YELLOW,
    Color.CYAN,
    Color.MAGENTA,
    Color.WHITE,
    Color.parseColor("#55FF55"),
    Color.parseColor("#FFA500"),
    Color.parseColor("#FF8888"),
    Color.parseColor("#AAAAFF"),
    Color.parseColor("#FFFFAA"),
    Color.parseColor("#55AAAA"),
    Color.parseColor("#AA33AA"),
    Color.parseColor("#0D0068")
  };
  final List<Pair<Float, RectF>> screenRects = new LinkedList<>();
  private final Logger logger = new Logger();
  private final Queue<Integer> availableColors = new LinkedList<>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<>();
  private List<Centroid> prevCentroids = new ArrayList<>();
  private List<Centroid> currCentroids = new ArrayList<>();
  private List<Centroid> points = new ArrayList<>();
  private List<Centroid> prevPoints = new ArrayList<>();
  private boolean[] idStatus = new boolean[100];
  private ArrayList <Pair <Integer,Integer> > idCounter = new ArrayList <> ();
  private final Paint boxPaint = new Paint();
  private ArrayList<RectF> tempCentroid = new ArrayList<>();
  private ArrayList<String> titles = new ArrayList<>();
  private int upCount = 0;
  private int downCount = 0;
  private final float textSizePx;
  private final BorderedText borderedText;
  private Matrix frameToCanvasMatrix;
  private int frameWidth;
  private int frameHeight;
  private int sensorOrientation;
  public MultiBoxTracker(final Context context) {
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  public synchronized void setFrameConfiguration(
      final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint idPaint = new Paint();
    idPaint.setColor(Color.GREEN);

    final Paint linePaint = new Paint();
    linePaint.setColor(Color.YELLOW);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);
    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }
    for(int i=0;i<points.size();i++){
      canvas.drawCircle(points.get(i).X, points.get(i).Y,10, idPaint);
    }

  }
  public synchronized void trackResults(final List<Recognition> results, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(results);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void draw(final Canvas canvas) {
    tempCentroid.clear();
    titles.clear();
    final boolean rotated = sensorOrientation % 180 == 90;

    final Paint idPaint = new Paint();
    idPaint.setColor(Color.GREEN);

    final Paint linePaint = new Paint();
    linePaint.setColor(Color.YELLOW);
    linePaint.setTextSize(50);

    final Paint idTextPaintGreen = new Paint();
    final float testTextSize = 48f;
    idTextPaintGreen.setTextSize(testTextSize);
    idTextPaintGreen.setColor(Color.GREEN);

    final Paint idTextPaintRed = new Paint();
    idTextPaintRed.setTextSize(testTextSize);
    idTextPaintRed.setColor(Color.RED);

    final float multiplier =
        Math.min(
            canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
            canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
        ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
            sensorOrientation,
            false);
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);
      tempCentroid.add(trackedPos);
      titles.add(recognition.title);
      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      final String labelString =
          !TextUtils.isEmpty(recognition.title)
              ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
              : String.format("%.2f", (100 * recognition.detectionConfidence));
                  //borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
       //labelString);

      //borderedText.drawText(
          //canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);
    }
    canvas.drawLine(0,canvas.getHeight()*(0.4f),canvas.getWidth(),(canvas.getHeight()*0.4f),linePaint);
    for(int i=0;i<points.size();i++) {
        if(points.get(i).label.equals("bus") || points.get(i).equals("truck")){
            canvas.drawText(String.valueOf(points.get(i).id)+points.get(i).label.charAt(0), points.get(i).X, points.get(i).Y+(points.get(i).height/4), idTextPaintGreen);
            canvas.drawCircle(points.get(i).X, points.get(i).Y+(points.get(i).height/4), 10, idTextPaintGreen);
            continue;
        }
        if (points.get(i).Y < (canvas.getHeight()*0.4f)){
            canvas.drawText(String.valueOf(points.get(i).id)+points.get(i).label.charAt(0), points.get(i).X, points.get(i).Y, idTextPaintGreen);
            canvas.drawCircle(points.get(i).X, points.get(i).Y, 10, idTextPaintGreen);
        }
        else{
            canvas.drawText(String.valueOf(points.get(i).id)+points.get(i).label.charAt(0), points.get(i).X, points.get(i).Y, idTextPaintRed);
            canvas.drawCircle(points.get(i).X, points.get(i).Y, 10, idTextPaintRed);
        }

        for(int j=0;j<prevPoints.size();j++){
            if(prevPoints.get(j).id == points.get(i).id){
                points.get(i).lineStatus = prevPoints.get(j).lineStatus;
                if(points.get(i).label.equals("bus") || points.get(i).label.equals("truck")) {
                    if (prevPoints.get(j).Y+(prevPoints.get(j).height/4) < (canvas.getHeight() * 0.4f) && points.get(i).Y+(points.get(i).height/4) > (canvas.getHeight() * 0.4f) && !points.get(i).lineStatus) {
                        points.get(i).lineStatus = true;
                        downCount++;
                    } else if (prevPoints.get(j).Y+(prevPoints.get(j).height/4) > (canvas.getHeight() * 0.4f) && points.get(i).Y+(points.get(i).height/4) < (canvas.getHeight() * 0.4f) && !points.get(i).lineStatus) {
                        points.get(i).lineStatus = true;
                        upCount++;
                    }
                }
                else {
                    if (prevPoints.get(j).Y < (canvas.getHeight() * 0.4f) && points.get(i).Y > (canvas.getHeight() * 0.4f) && !points.get(i).lineStatus) {
                        points.get(i).lineStatus = true;
                        downCount++;
                    } else if (prevPoints.get(j).Y > (canvas.getHeight() * 0.4f) && points.get(i).Y < (canvas.getHeight() * 0.4f) && !points.get(i).lineStatus) {
                        points.get(i).lineStatus = true;
                        upCount++;
                    }
                }
                prevPoints.remove(prevPoints.get(j));
                break;
            }
        }
    }
    canvas.drawText("Up Count : " + String.valueOf(upCount), 0, canvas.getHeight()*0.8f, linePaint);
    canvas.drawText("Down Count : " + String.valueOf(downCount), 0,canvas.getHeight()*0.85f,linePaint);
  }

  private void processResults(final List<Recognition> results) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<>();


    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v(
          "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<>(result.getConfidence(), result));
    }

    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }
    prevPoints.clear();
    prevPoints.addAll(points);
    points.clear();
    prevCentroids.addAll(currCentroids);
    trackedObjects.clear();
    currCentroids.clear();

    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();
      trackedRecognition.color = COLORS[trackedObjects.size()];
      trackedObjects.add(trackedRecognition);
      if (trackedObjects.size() >= COLORS.length) {
        break;
      }
    }
    for(int i=0;i<tempCentroid.size();i++){
     currCentroids.add(new Centroid(tempCentroid.get(i).centerX(), tempCentroid.get(i).centerY(),titles.get(i), tempCentroid.get(i).bottom - tempCentroid.get(i).top));
    }
    countObjects(prevCentroids, currCentroids);
  }

  private double euclidianDist(Centroid c1, Centroid c2){
    return Math.sqrt(Math.pow((c1.X-c2.X),2) + Math.pow((c1.Y-c2.Y),2));
  }

  private int get_new_Id(){
      int i;
      for(i = 0; i < 100; i++) {
          if(!idStatus[i]) {
              idStatus[i] = true;
              break;
          }
      }
      return i;
  }
  private void countObjects(List<Centroid> prevCentroids, List<Centroid> currCentroids){
    int i,j;
    boolean flag;
    double min_dist = 200;
    for(i=currCentroids.size()-1;i>=0;i--){
        for(j=i-1;j>=0;j--){
            if(euclidianDist(currCentroids.get(i), currCentroids.get(j)) <= 50){
                currCentroids.remove(currCentroids.get(i));
                break;
            }
        }
    }
    for(i = 0; i < currCentroids.size(); i++) {
        flag = false;
      for (j = 0; j < prevCentroids.size(); j++) {
          double temp_dist = euclidianDist(currCentroids.get(i), prevCentroids.get(j));
          if (temp_dist < min_dist){
              currCentroids.get(i).id = prevCentroids.get(j).id;
              prevCentroids.remove(prevCentroids.get(j));
              for(int k=0;k<idCounter.size();k++){
                  if(idCounter.get(k).first == currCentroids.get(i).id){
                      idCounter.remove(idCounter.get(k));
                      idCounter.add(new Pair<>(currCentroids.get(i).id, 0));
                      break;
                  }
              }
              points.add(currCentroids.get(i));
              flag = true;
              break;
        }
      }
      if(!flag){
        currCentroids.get(i).id = get_new_Id();
        idCounter.add(new Pair<>(currCentroids.get(i).id , 0));
        points.add(currCentroids.get(i));

      }
    }

    for(i = 0; i < prevCentroids.size(); i++){
        boolean flag1 = false;
        for(int k=0;k<currCentroids.size();k++){
            if(euclidianDist(currCentroids.get(k), prevCentroids.get(i)) <= 50){
                flag1 = true;
            }
        }
        for(j = 0; j < idCounter.size(); j++){
            if((prevCentroids.get(i).id == idCounter.get(j).first && idCounter.get(j).second >= 10) || flag1){
                idStatus[idCounter.get(j).first] = false;
                prevCentroids.remove(prevCentroids.get(i));
                idCounter.remove(idCounter.get(j));
                break;
            }
            else if(prevCentroids.get(i).id == idCounter.get(j).first){
              int temp = idCounter.get(j).second + 1;
              idCounter.remove(idCounter.get(j));
              idCounter.add(new Pair<>(prevCentroids.get(i).id , temp));
              points.add(prevCentroids.get(i));
              break;
            }
        }
    }
  }

  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }
  private class Centroid {
    int id;
    float X, Y, height;
    boolean lineStatus;
    String label;
    Centroid(Float X, Float Y, String label, Float height) {
      this.id = -1;
      this.X = X;
      this.Y = Y;
      this.lineStatus = false;
      this.label = label;
      this.height = height;
    }
  }
}
