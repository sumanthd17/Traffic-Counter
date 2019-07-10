package org.tensorflow.lite.examples.detection.Database;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";
    private static final int DATABASE_VERSION = 1;
    private static DBHelper instance;
    public static final String DATABASE_NAME = "rbtrafficcounter.db";
    private final Context context;
    public static final String TRIP_COLUMN_ID = "id";
    public static final String TRIP_COLUMN_NAME = "trip_name";
    public static final String TRIP_COLUMN_VIDEO_NAME = "video_name";
    public static final String TRIP_COLUMN_LOG_FILE_NAME = "log_file_name";
    public static final String TRIP_COLUMN_USER_NAME = "user_name";
    public static final String TRIP_COLUMN_DATE = "date";

    String PASSWORD = "rbtraffic";
    private String tripCreateTableString = "create table trip (id int, trip_name text, video_name text, log_file_name text, user_name text, date text)";


    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context);
        this.context = context;
    }

    static public synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(tripCreateTableString);
        try {
            for (int i = 1; i < DATABASE_VERSION; ++i) {
                String migrationName = "from_" + i + "_to_" + (i + 1) + ".sql";
                Log.d(TAG, "Looking for migration file: " + migrationName);
                readAndExecuteSQLScript(db, context, migrationName);
            }
        } catch (Exception exception) {
            Log.e(TAG, "Exception running upgrade script:", exception);
        }
        Log.d(TAG, "onCreate: database create call");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Updating table from " + oldVersion + " to " + newVersion);
        // You will not need to modify this unless you need to do some android specific things.
        // When upgrading the database, all you need to do is add a file to the assets folder and name it:
        // from_1_to_2.sql with the version that you are upgrading to as the last version.
        try {
            for (int i = oldVersion; i < newVersion; ++i) {
                String migrationName = "from_" + i + "_to_" + (i + 1) + ".sql";
                Log.d(TAG, "Looking for migration file: " + migrationName);
                readAndExecuteSQLScript(db, context, migrationName);
            }
        } catch (Exception exception) {
            Log.e(TAG, "Exception running upgrade script:", exception);
        }
    }

    private void readAndExecuteSQLScript(SQLiteDatabase db, Context ctx, String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            Log.d(TAG, "SQL script file name is empty");
            return;
        }

        Log.d(TAG, "Script found. Executing...");
        AssetManager assetManager = ctx.getAssets();
        BufferedReader reader = null;

        try {
            InputStream is = assetManager.open(fileName);
            InputStreamReader isr = new InputStreamReader(is);
            reader = new BufferedReader(isr);
            executeSQLScript(db, reader);
        } catch (IOException e) {
            Log.e(TAG, "IOException:", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException:", e);
                }
            }
        }
    }

    private void executeSQLScript(SQLiteDatabase db, BufferedReader reader) throws IOException {
        String line;
        StringBuilder statement = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            statement.append(line);
            statement.append("\n");
            if (line.endsWith(";")) {
                db.execSQL(statement.toString());
                statement = new StringBuilder();
            }
        }
    }

    public String insertTrip(String trip_name, String video_name, String log, String user_name, String date) {
        SQLiteDatabase db = getWritableDatabase(PASSWORD);
        ContentValues contentValues = new ContentValues();

        int id = 0;

        Cursor res = db.rawQuery("select count(*) from trip ", null);

        if (res.getCount() > 0){
            res.moveToFirst();
            id = res.getInt(0) + 1;
        }

        res.close();

        contentValues.put(TRIP_COLUMN_ID, id);
        contentValues.put(TRIP_COLUMN_NAME, trip_name);
        contentValues.put(TRIP_COLUMN_VIDEO_NAME, video_name);
        contentValues.put(TRIP_COLUMN_LOG_FILE_NAME, log);
        contentValues.put(TRIP_COLUMN_USER_NAME, user_name);
        contentValues.put(TRIP_COLUMN_DATE, date);


        db.insert("trip", null, contentValues);
        return String.valueOf(id);
    }

    public Integer deleteTripByName(String v_name) {
        SQLiteDatabase db = getWritableDatabase(PASSWORD);
        int row = db.delete("trip", "video_name = ? ", new String[]{v_name});
        return row;
    }

    public Cursor getTripData() {
        SQLiteDatabase db = getReadableDatabase(PASSWORD);
        Cursor res = db.rawQuery("select * from trip", null);
        Log.d(TAG, "getTripData: Trip Count  : " + res.getCount());
        db.close();
        return res;
    }
}
