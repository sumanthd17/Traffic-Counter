package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;

import org.tensorflow.lite.examples.detection.Database.DBHelper;

import java.io.File;
import java.util.ArrayList;

public class PreviousRidesActivity extends AppCompatActivity {

    private static final String TAG = "PreviousRidesActivity";
    private Context context;
    VideoView videoView;
    ArrayList<String> videoList;
    ArrayList<String> videoLocationList;
    private RecyclerView rvSavedRides;
    MyAdapter mAdapter;
    private DBHelper dbHelper;
    ArrayList<SavedRidesDataModel> data = new ArrayList<>();
    ArrayList<String> videoNameList;
    ArrayList<String> videoPathList;

    private static final int MY_PERMISSION_REQUEST = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_previous_rides);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        videoView = findViewById(R.id.videoDisplay);
        MediaController mediaController = new MediaController(PreviousRidesActivity.this);
        mediaController.setAnchorView(videoView);
        if(ContextCompat.checkSelfPermission(PreviousRidesActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(PreviousRidesActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)){
                ActivityCompat.requestPermissions(PreviousRidesActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_REQUEST);
            }
            else{
                ActivityCompat.requestPermissions(PreviousRidesActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_REQUEST);
            }
        }
        else{
            playSavedVideo();
        }



        context = this;
        dbHelper = new DBHelper(this);

        rvSavedRides = findViewById(R.id.rvSavedRides);
        mAdapter = new MyAdapter();
        mAdapter.notifyDataSetChanged();
        new getAllData().doInBackground();
        new getAllData().onPostExecute();
    }

    private void playSavedVideo() {

    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private class getAllData{//} extends AsyncTask<String, String, String> {
        //ProgressDialog progressDialog;

        //@Override
        /*protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Fetching data..");
            progressDialog.setCancelable(false);
            progressDialog.show();

        }*/

        //@Override
        protected void doInBackground() {

            videoNameList = new ArrayList<>();
            videoPathList = new ArrayList<>();
            ContentResolver contentResolver = getContentResolver();
            Uri videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            Cursor videoCursor = contentResolver.query(videoUri, null, null, null, null);
            if(videoCursor != null && videoCursor.moveToFirst()){
                int videotitle = videoCursor.getColumnIndex(MediaStore.Video.Media.TITLE);
                int videoLocation = videoCursor.getColumnIndex(MediaStore.Video.Media.DATA);
                do{
                    String currentTitle = videoCursor.getString(videotitle);
                    String currentPath = videoCursor.getString(videoLocation);
                    if(currentPath.indexOf("RoadBounce") != -1){
                        videoPathList.add(currentPath);
                        videoNameList.add(currentTitle);
                    }
                }while(videoCursor.moveToNext());
            }
            // return "Success";
        }



        //@Override
        protected void onPostExecute() {
            //progressDialog.dismiss();
            //super.onPostExecute(result);


            if (!videoNameList.isEmpty()){
                rvSavedRides.setVisibility(View.VISIBLE);


                rvSavedRides.setLayoutManager(new GridLayoutManager(context, 1));
                rvSavedRides.setAdapter(mAdapter);
                mAdapter.notifyDataSetChanged();

                TextView empty_view = findViewById(R.id.tvEmpty_view);
                empty_view.setVisibility(View.GONE);
            } else {
                rvSavedRides.setVisibility(View.GONE);

                TextView empty_view = findViewById(R.id.tvEmpty_view);
                empty_view.setVisibility(View.VISIBLE);
            }
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {

        public class MyViewHolder extends RecyclerView.ViewHolder {
            public TextView tvTitle;//, tvDate;
            ImageView ivPlayVideo, ivDeleteVideo;

            public MyViewHolder(View view) {
                super(view);
                tvTitle =  view.findViewById(R.id.tvTitle);
                //tvDate =  view.findViewById(R.id.tvDate);
                ivPlayVideo =  view.findViewById(R.id.ivPlayVideo);
                ivDeleteVideo =  view.findViewById(R.id.ivDeleteVideo);
            }
        }

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_rides, parent, false);

            return new MyViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            Glide.with(context)
                    .load(R.drawable.play_video)
                    .into(holder.ivPlayVideo);

            Glide.with(context)
                    .load(R.drawable.delete)
                    .into(holder.ivDeleteVideo);

            holder.tvTitle.setText(videoNameList.get(position));
//holder.tvDate.setText(data.get(position).getDate());

            holder.ivPlayVideo.setOnClickListener(v ->{
                videoView.setVideoURI(Uri.parse(videoPathList.get(position)));
                videoView.setMediaController(new MediaController(PreviousRidesActivity.this));
                videoView.requestFocus();
                videoView.start();
            });

            holder.ivDeleteVideo.setOnClickListener(v -> {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle("Alert");
                alertDialogBuilder.setMessage("Are you sure?");
                alertDialogBuilder.setCancelable(true);

                alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        //int row = dbHelper.deleteTripByName(data.get(pos).getVideoName());
                        File testFile = new File(videoPathList.get(position));
                        testFile.delete();
                        new getAllData().doInBackground();
                        new getAllData().onPostExecute();
                        //Log.d(TAG, "on trip delete: " + row);
                        Toast.makeText(context, "Deleted Successfully!", Toast.LENGTH_SHORT).show();
                    }
                });

                alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();

            });
        }

        @Override
        public int getItemCount() {
            return videoNameList.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

    }

}
