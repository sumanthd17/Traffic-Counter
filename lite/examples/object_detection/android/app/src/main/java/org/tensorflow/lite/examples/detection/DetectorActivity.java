/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.html.WebColors;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.tensorflow.lite.examples.detection.Database.DBHelper;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final String TAG = "DetectorActivity";

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.55f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;

    private DBHelper dbHelper;
    String tripName = "Traffic-data1111";
    SharedPreferences sharedPreferences;
    public static final String MyPREFERENCES = "MyPrefs";
    public static final String UsernameKey = "UsernameKey";

    //screen recording essentials
    private static final int REQUEST_CODE = 1000;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private MediaProjectionCallback mMediaProjectionCallback;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSION_KEY = 1;
    boolean isRecording = false;
    String videoFileName;
    String pdfFileName;


    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

//    private final LocationListener mLocationListener = new LocationListener() {
//        @Override
//        public void onLocationChanged(final Location location) {
//            //your code here
//        }
//
//        @Override
//        public void onStatusChanged(String provider, int status, Bundle extras) {
//
//        }
//
//        @Override
//        public void onProviderEnabled(String provider) {
//
//        }
//
//        @Override
//        public void onProviderDisabled(String provider) {
//
//        }
//    };
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
//
//        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME,
//                LOCATION_REFRESH_DISTANCE, mLocationListener);
//    }

    List<Classifier.Recognition> results;

    public void EndTrip(View view) {
        Log.d(TAG, "EndTrip: ");
//        Log.d(TAG, "Accessed Location.");


        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
        String format = simpleDateFormat.format(new Date());

        String filename = "Log-" + format + ".txt";

        try {

            File filePath = new File(Environment.getExternalStorageDirectory() + "/RoadBounce/LOGS");

            if (!filePath.isDirectory()) {
                filePath.mkdirs();
            }
            File txtFile = new File(filePath, filename);
            FileWriter writer = new FileWriter(txtFile);
            writer.append("Trip Summary for" + tripName + "\n");

            for (final Classifier.Recognition result : results) {
                final RectF location = result.getLocation();
                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                    case TF_OD_API:
                        minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        break;
                }
                if (location != null
                        && result.getConfidence() >= minimumConfidence
                        && (result.getTitle().equals("car") || result.getTitle().equals("motorcycle")
                        || result.getTitle().equals("bus") || result.getTitle().equals("truck"))) {
                    writer.append(String.valueOf(result.getTitle()) + " @ " + String.valueOf(simpleDateFormat.format(new Date())));
                }
            }
            writer.append("Trip Terminated at" + format);
            writer.flush();
            writer.close();

            generatePdf();

        }catch (Exception e){
            e.printStackTrace();
        }
//        stopRec();

        //get username from session
//        sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
//        String username = sharedPreferences.getString(UsernameKey,"");
//        SimpleDateFormat date = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");
//        String strDate = date.format(new Date());
//
//        String id = dbHelper.insertTrip(tripName, videoFileName, filename, username, strDate);
//
//        Log.d(TAG, "Trip Details: " + tripName + " " + videoFileName + " " + filename + " " + username + " " + strDate);
//        Log.d(TAG, "Trip added to sqlite id: " + id);

        finish();
    }




    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

//        new StartDetectionAsync().execute();

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();


                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence && (result.getTitle().equals("car") || result.getTitle().equals("motorcycle") || result.getTitle().equals("bus") || result.getTitle().equals("truck"))) {
                                canvas.drawRect(location, paint);
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        //printing details
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    public class StartDetectionAsync extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... strings) {
            final long currTimestamp = timestamp;
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            results = detector.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
                case TF_OD_API:
                    minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                    break;
            }

            List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();


            for (final Classifier.Recognition result : results) {
                final RectF location = result.getLocation();
                if (location != null
                        && result.getConfidence() >= minimumConfidence
                        && (result.getTitle().equals("car") || result.getTitle().equals("motorcycle") || result.getTitle().equals("bus") || result.getTitle().equals("truck"))) {
                    canvas.drawRect(location, paint);
                    cropToFrameTransform.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);
                }
            }

            tracker.trackResults(mappedRecognitions, currTimestamp);
            trackingOverlay.postInvalidate();

            computingDetection = false;

            runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            //printing details
                            showFrameInfo(previewWidth + "x" + previewHeight);
                            showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                            showInference(lastProcessingTimeMs + "ms");
                        }
                    });
            return null;
        }
    }
    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    //video recording

    protected void startVideoRecording(){
        dbHelper = new DBHelper(this);
        Intent i = getIntent();

        tripName = i.getStringExtra("name");

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        initRecorder();
        shareScreen();
    }

    private void shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
        isRecording = true;
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.getSurface(), null, null);
    }

    private void initRecorder() {
        try {
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); //THREE_GPP

            String time = new SimpleDateFormat("dd-MM-yyyy-hh_mm_ss")
                    .format(new Date());

            videoFileName = new StringBuilder("tc_video_").append(time).append(".mp4").toString();
            pdfFileName = new StringBuilder("tc_video_").append(time).append(".pdf").toString();

            mMediaRecorder.setOutputFile(Environment.getExternalStorageDirectory()
                    + new StringBuilder("/RoadBounce/").append(videoFileName).toString());
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setVideoEncodingBitRate(512 * 1000);
            mMediaRecorder.setVideoFrameRate(16); // 30
            mMediaRecorder.setVideoEncodingBitRate(3000000);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRec(){
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        stopScreenSharing();

        //scan file so that it will appear in gallery.
        File path = new File(Environment.getExternalStorageDirectory() + "/RoadBounce/");
        if (!path.isDirectory()) {
            path.mkdirs();
        }
        File file = new File(path, videoFileName);

        Intent mediaScannerIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri fileContentUri = Uri.fromFile(file); // With 'file' being the File object
        mediaScannerIntent.setData(fileContentUri);
        this.sendBroadcast(mediaScannerIntent); // With 'this' being the context, e.g. the activity
    }

    @Override
    public synchronized void onDestroy() {
        LOGGER.d("onDestroy " + this);
        super.onDestroy();
//        destroyMediaProjection();
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        destroyMediaProjection();
        isRecording = false;
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }

        Log.i("CameraActivity", "MediaProjection Stopped");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e("CameraActivity", "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            isRecording = false;
            return;
        }
        mMediaProjectionCallback = new MediaProjectionCallback();
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
        isRecording = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (isRecording) {
                isRecording = false;
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            }
            mMediaProjection = null;
            stopScreenSharing();
        }
    }
    //end screen recording

    private Font headerFont;
    private Font bf9;
    private Font bf10;
    private Font bfBold12;
    private Font bf12;
    private boolean isBgColor = false;
    private String redColor = "#FFC7CE";//"9C0006";
    private String yellowColor ="#FFEB9C"; //"9C6500";
    private String greenColor = "#C6EFCE";//"006100";
    private String whiteColor = "#ffffff";
    private Font redFont;
    private Font greenFont;
    private Font yellowFont;
    private static final String LEFT_FOOTER_PART1 = "Generated by RoadBounce - www.roadbounce.com" ;;

    private void generatePdf() {
        String fontpath = (getApplicationContext().getAssets() + "fonts/");
        BaseFont customfont = null;
        try {
            customfont = BaseFont.createFont(fontpath + "Calibri.ttf", BaseFont.CP1252, BaseFont.EMBEDDED);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // special fonts sizes
        bfBold12 = new Font(customfont, 11, Font.BOLD, new BaseColor(0, 0, 0));

        // special fonts sizes
        bf9 = new Font(customfont,9);
        bf10 = new Font(customfont,9);
        headerFont = new Font(customfont, 14, Font.BOLD, new BaseColor(0,
                112, 192));



        bf12 = new Font(customfont, 11);



       /* bfBold12 = new Font(customfont, 12, Font.BOLD, new BaseColor(0, 0, 0));
        bf12 = new Font(customfont, 12);*/

       /* redFont = new Font(customfont, 12, Font.NORMAL, new BaseColor(255, 199, 206));
        greenFont = new Font(customfont, 12, Font.NORMAL, new BaseColor(198, 239, 206));
        yellowFont = new Font(customfont, 12, Font.NORMAL, new BaseColor(255, 235, 156));*/
        redFont = new Font(customfont, 12, Font.NORMAL, new BaseColor(156, 0, 6));
        greenFont = new Font(customfont, 12, Font.NORMAL, new BaseColor(0, 97, 0));
        yellowFont = new Font(customfont, 12, Font.NORMAL, new BaseColor(156, 101, 0));

        Document doc = new Document();
        PdfWriter docWriter = null;


        try {
            File PWD_RQMDirectory = new File(Environment.getExternalStorageDirectory() + "/RoadBounce/PDF");
            // have the object build the directory structure, if needed.
            if (!PWD_RQMDirectory.isDirectory()) {
                PWD_RQMDirectory.mkdirs();
            }
            File path = new File(PWD_RQMDirectory, pdfFileName);
            docWriter = PdfWriter.getInstance(doc, new FileOutputStream(path));

            //document header attributes
            doc.addAuthor("Definitics Solutions");
            doc.addCreationDate();
            doc.addProducer();
            doc.addCreator("Definitics.com");
            doc.addTitle("Report with Column Headings");
            doc.setPageSize(PageSize.A4);
            Header header = new Header();
            header.footerName=LEFT_FOOTER_PART1;
            String buLevelTree= "";
            header.headerDistrict=buLevelTree;
            header.isIndexPage=false;

            docWriter.setPageEvent(header);
            //open document
            doc.open();

            //create a paragraph
            Paragraph paragraph = new Paragraph("Location: " + tripName + "\n");

            float[] columnWidths = {0.5f, 1f, 1f, 1.5f};
            //create PDF table with the given widths
            PdfPTable table = new PdfPTable(columnWidths);

            // set table width a percentage of the page width
            table.setWidthPercentage(100f);

            //insert column headings
            insertCell(table, "Traffic Counting Report", Element.ALIGN_CENTER, 7, headerFont, whiteColor);
            insertCell(table, "#", Element.ALIGN_CENTER, 1, bfBold12, whiteColor);
            insertCell(table, "Vehicle", Element.ALIGN_CENTER, 1, bfBold12, whiteColor);
//            insertCell(table, "Location", Element.ALIGN_CENTER, 1, bfBold12, whiteColor);
            insertCell(table, "Accuracy", Element.ALIGN_CENTER, 1, bfBold12, whiteColor);
            insertCell(table, "Time", Element.ALIGN_CENTER, 1, bfBold12,whiteColor);

            table.setHeaderRows(2);

            int srN0 = 1;
            if (!results.isEmpty()) {
                for (final Classifier.Recognition result : results) {
                    final RectF location = result.getLocation();
                    float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                    switch (MODE) {
                        case TF_OD_API:
                            minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                            break;
                    }
                    if (location != null
                            && result.getConfidence() >= minimumConfidence
                            && (result.getTitle().equals("car") || result.getTitle().equals("motorcycle")
                            || result.getTitle().equals("bus") || result.getTitle().equals("truck"))) {

                        insertCell(table, (srN0) + "", Element.ALIGN_CENTER, 1, bf12, whiteColor);
                        srN0++;
                        insertCell(table, result.getTitle(), Element.ALIGN_CENTER, 1, bf12, whiteColor);//vehicle type
//                        insertCell(table, String.valueOf(result.getLocation()), Element.ALIGN_CENTER, 1, bf12, whiteColor);//location
                        insertCell(table, String.valueOf(result.getConfidence() * 100), Element.ALIGN_CENTER, 1, bf12, whiteColor);//confidence
                        insertCell(table, result.getTimeStamp(), Element.ALIGN_CENTER, 1, bf12, whiteColor);//timestamp

                    }
                }
            } else {
                insertCell(table, "No data recorded.", Element.ALIGN_CENTER, 7, bf12, redColor);
            }

            //add the PDF table to the paragraph
            paragraph.add(table);

            // add the paragraph to the document
            doc.add(paragraph);


        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (doc != null) {
                //close the document
                doc.close();
            }
            if (docWriter != null) {
                //close the writer

                File path = new File(Environment.getExternalStorageDirectory() + "/RoadBounce/PDF");
                if (!path.isDirectory()) {
                    path.mkdirs();
                }
                File file = new File(path, pdfFileName);

                Intent mediaScannerIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri fileContentUri = Uri.fromFile(file); // With 'file' being the File object
                mediaScannerIntent.setData(fileContentUri);
                this.sendBroadcast(mediaScannerIntent); // With 'this' being the context, e.g. the activity
                docWriter.close();
            }
        }
    }

    private void insertCell(PdfPTable table, String text, int align, int colspan, Font font, String color) {

        /*Font myfont = fonts;

        if (color.equals(whiteColor)) {
            myfont.setColor(BaseColor.BLACK);
        } else if (color.equals(greenColor)) {
            myfont.setColor(BaseColor.GREEN);
        } else if (color.equals(redColor)) {
            myfont.setColor(BaseColor.RED);
        } else {
            myfont.setColor(BaseColor.YELLOW);
        }*/
        //create a new cell with the specified Text and Font
        PdfPCell cell = new PdfPCell(new Phrase(text.trim(), font));
        //set the cell alignment
        cell.setHorizontalAlignment(align);

        //set the cell column span in case you want to merge two or more cells
        cell.setColspan(colspan);
        //in case there is no text and you wan to create an empty row
        BaseColor myColor = WebColors.getRGBColor(color);
        cell.setBackgroundColor(myColor);
        /*if (isBgColor) {

            BaseColor myColor = WebColors.getRGBColor(color);
            cell.setBackgroundColor(myColor);
        }*/

        if (text.trim().equalsIgnoreCase("")) {
            cell.setMinimumHeight(10f);
        }
        cell.setPadding(5f);
        cell.setUseAscender(true);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        //add the call to the table
        table.addCell(cell);

    }

    class Header extends PdfPageEventHelper {
        //Font fonts;
        PdfTemplate t;
        Image total;

        PdfContentByte cb;

        String headerDistrict;
        String footerName;
        boolean isIndexPage;



        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            t = writer.getDirectContent().createTemplate(30, 16);
            cb = writer.getDirectContent();
            try {
                total = Image.getInstance(t);
                total.setRole(PdfName.ASCENT);
            } catch (DocumentException de) {
                throw new ExceptionConverter(de);
            }
        }

        @Override
        public void onStartPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            if(!isIndexPage){
               /* Phrase header = new Phrase(DOCUMENT_HEADER,
                        headerFont);

                ColumnText.showTextAligned(
                        cb,
                        Element.ALIGN_CENTER,
                        header,
                        (document.right() + document.left()) / 2
                        , document.top() +15, 0);*/

              /*  Phrase rightHeader = new Phrase(headerDistrict,
                        bf9);

                ColumnText.showTextAligned(
                        cb,
                        Element.ALIGN_RIGHT,
                        rightHeader,
                        document.right() + 25
                        , document.top() + 36, 90);*/
            }else{
                Phrase header = new Phrase("Contents",
                        headerFont);

                ColumnText.showTextAligned(
                        cb,
                        Element.ALIGN_LEFT,
                        header,
                        document.left()
                        , document.top() + 10, 0);
            }

        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable table = new PdfPTable(3);
            try {
                if(!isIndexPage){
                    table.setWidths(new int[]{9, 2, 1});
                    table.setTotalWidth(document.getPageSize().getWidth());
                    table.getDefaultCell().setFixedHeight(20);
                    table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
                    table.addCell(new Phrase(footerName, bf10));
                    table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(new Phrase(String.format("Page %d of", writer.getPageNumber()), bf10));
                    PdfPCell cell = new PdfPCell(total);
                    cell.setBorder(Rectangle.NO_BORDER);
                    table.addCell(cell);
                    PdfContentByte canvas = writer.getDirectContent();
                    canvas.beginMarkedContentSequence(PdfName.ARTIFACT);
                    table.writeSelectedRows(0, -1, 25, 25, canvas);
                    canvas.endMarkedContentSequence();
                }
            } catch (DocumentException de) {
                throw new ExceptionConverter(de);
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if(!isIndexPage){
                ColumnText.showTextAligned(t, Element.ALIGN_LEFT,
                        new Phrase(String.valueOf(writer.getPageNumber()), bf10),
                        2, 5, 0);
            }
        }
    }

}
