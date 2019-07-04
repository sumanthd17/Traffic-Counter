package org.tensorflow.lite.examples.detection;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.tensorflow.lite.examples.detection.Database.DBHelper;

import java.util.ArrayList;

public class PreviousRidesActivity extends AppCompatActivity {

    private static final String TAG = "PreviousRidesActivity";
    private Context context;
    private RecyclerView rvSavedRides;
    MyAdapter mAdapter;
    private DBHelper dbHelper;
    ArrayList<SavedRidesDataModel> data = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_previous_rides);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        context = this;
        dbHelper = new DBHelper(this);

        rvSavedRides = findViewById(R.id.rvSavedRides);
        mAdapter = new MyAdapter();
        mAdapter.notifyDataSetChanged();

        new getAllData().execute();
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

    private class getAllData extends AsyncTask<String, String, String> {
        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Fetching data..");
            progressDialog.setCancelable(false);
            progressDialog.show();

        }

        @Override
        protected String doInBackground(String... strings) {

            data.clear();

            Cursor cursorId = dbHelper.getTripData();
            if(cursorId!=null && cursorId.getCount()>0) {
                cursorId.moveToFirst();

                while (!cursorId.isAfterLast()) {

                    int id = cursorId.getInt(0);
                    String name = cursorId.getString(1);
                    String videoName = cursorId.getString(2);
                    String logName = cursorId.getString(3);
                    String username = cursorId.getString(4);
                    String date = cursorId.getString(5);

                    data.add(new SavedRidesDataModel(id, name, videoName, logName, username, date));

                    cursorId.moveToNext();
                }
            }
            if (!cursorId.isClosed()) {
                cursorId.close();
            }


            return "Success";
        }



        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();
            super.onPostExecute(result);


            if (!data.isEmpty()){
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
            public TextView tvTitle, tvDate;
            ImageView ivPlayVideo, ivDeleteVideo;

            public MyViewHolder(View view) {
                super(view);
                tvTitle =  view.findViewById(R.id.tvTitle);
                tvDate =  view.findViewById(R.id.tvDate);
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
            final int pos = position;

            Glide.with(context)
                    .load(R.drawable.play_video)
                    .into(holder.ivPlayVideo);

            Glide.with(context)
                    .load(R.drawable.delete)
                    .into(holder.ivDeleteVideo);

            holder.tvTitle.setText(data.get(position).getName());
            holder.tvDate.setText(data.get(position).getDate());

            holder.ivPlayVideo.setOnClickListener(v ->
                    Toast.makeText(context, "Play video...", Toast.LENGTH_SHORT).show());

            holder.ivDeleteVideo.setOnClickListener(v -> {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle("Alert");
                alertDialogBuilder.setMessage("Are you sure?");
                alertDialogBuilder.setCancelable(true);

                alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        int row = dbHelper.deleteTripByName(data.get(pos).getVideoName());

                        new getAllData().execute();
                        Log.d(TAG, "on trip delete: " + row);
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
            return data.size();
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
