package net.exent.flywithme.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.exent.flywithme.bean.Takeoff;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class Database extends SQLiteOpenHelper {
    private static Lock databaseLock = new ReentrantLock();
    private static Database instance;

    private Database(Context context) {
        super(context, "flywithme", null, 5);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(getClass().getName(), "onCreate()");
        createDatabase(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(getClass().getName(), "onUpgrade(" + oldVersion + ", " + newVersion + ")");
        if (oldVersion == 1)
            upgradeDatabaseToV2(db);
        if (oldVersion == 2)
            upgradeDatabaseToV3(db);
        if (oldVersion == 3)
            upgradeDatabaseToV4(db);
        if (oldVersion == 4)
            upgradeDatabaseToV5(db);
    }

    public static Takeoff getTakeoff(Context context, long takeoffId) {
        Takeoff takeoff = null;
        try {
            SQLiteDatabase db = acquire(context).getReadableDatabase();
            if (db == null)
                throw new IllegalArgumentException("Unable to get database object");
            Cursor cursor = db.query("takeoff", Takeoff.COLUMNS, "takeoff_id = " + takeoffId, null, null, null, null);
            if (cursor.moveToNext())
                takeoff = Takeoff.create(new ImprovedCursor(cursor));
            cursor.close();
            db.close();
        } finally {
            release();
        }
        return takeoff;
    }

    public static void updateTakeoff(Context context, Takeoff takeoff) {
        if (takeoff == null) {
            Log.w("Database", "Unable to update takeoff, argument is null");
            return;
        }
        try {
            SQLiteDatabase db = acquire(context).getWritableDatabase();
            if (db == null)
                throw new IllegalArgumentException("Unable to get database object");
            ContentValues contentValues = takeoff.getContentValues();
            if (db.update("takeoff", contentValues, "takeoff_id = " + takeoff.getId(), null) <= 0) {
                // no rows updated, insert instead
                db.insert("takeoff", null, contentValues);
            }
            db.close();
        } finally {
            release();
        }
    }

    public static List<Takeoff> getTakeoffs(Context context, double latitude, double longitude, int maxResult, boolean includeFavourites) {
        List<Takeoff> takeoffs = new ArrayList<>();
        if (maxResult <= 0)
            return takeoffs;
        // order result by approximate distance
        double latitudeRadians = latitude * Math.PI / 180.0;
        double latitudeCos = Math.cos(latitudeRadians);
        double latitudeSin = Math.sin(latitudeRadians);
        double longitudeRadians = longitude * Math.PI / 180.0;
        double longitudeCos = Math.cos(longitudeRadians);
        double longitudeSin = Math.sin(longitudeRadians);
        String orderBy = includeFavourites ? "favourite desc, " : "";
        orderBy += "(" + latitudeCos + " * latitude_cos * (longitude_cos * " + longitudeCos + " + longitude_sin * " + longitudeSin + ") + " + latitudeSin + " * latitude_sin) desc";

        // execute the query
        try {
            SQLiteDatabase db = acquire(context).getReadableDatabase();
            if (db == null)
                throw new IllegalArgumentException("Unable to get database object");
            Cursor cursor = db.rawQuery("select * from takeoff order by " + orderBy + " limit " + maxResult, null);
            while (cursor.moveToNext())
                takeoffs.add(Takeoff.create(new ImprovedCursor(cursor)));
            cursor.close();
            db.close();
        } finally {
            release();
        }
        return takeoffs;
    }

    public static void updateFavourite(Context context, Takeoff takeoff) {
        if (takeoff == null) {
            Log.w("Database", "Unable to update takeoff, argument is null");
            return;
        }
        try {
            SQLiteDatabase db = acquire(context).getWritableDatabase();
            if (db == null)
                throw new IllegalArgumentException("Unable to get database object");
            ContentValues contentValues = new ContentValues();
            contentValues.put("favourite", takeoff.isFavourite() ? 1 : 0);
            db.update("takeoff", contentValues, "takeoff_id = " + takeoff.getId(), null);
            db.close();
        } finally {
            release();
        }
    }

    private static Database acquire(Context context) {
        databaseLock.lock();
        if (instance == null)
            instance = new Database(context);
        return instance;
    }

    private static void release() {
        databaseLock.unlock();
    }

    /**
     * Create the most recent database layout.
     * This is only called when there's no existing database on device (fresh install).
     * @param db The SQLite database.
     */
    private synchronized void createDatabase(SQLiteDatabase db) {
        db.execSQL("create table takeoff(takeoff_id integer primary key, last_updated integer not null default current_timestamp, name text not null default '', description text not null default '', asl integer not null default 0, height integer not null default 0, latitude real not null default 0.0, latitude_cos real not null default 0.0, latitude_sin real not null default 0.0, longitude real not null default 0.0, longitude_cos real not null default 0.0, longitude_sin real not null default 0.0, exits integer not null default 0, favourite integer not null default 0)");
    }

    private synchronized void upgradeDatabaseToV2(SQLiteDatabase db) {
        // create new tables
        db.execSQL("create table schedule(takeoff_id integer not null, timestamp integer not null, pilot_name text not null, pilot_phone text not null)");
        db.execSQL("create table takeoff(takeoff_id integer primary key, name text not null default '', description text not null default '', asl integer not null default 0, height integer not null default 0, latitude real not null default 0.0, latitude_cos real not null default 0.0, latitude_sin real not null default 0.0, longitude real not null default 0.0, longitude_cos real not null default 0.0, longitude_sin real not null default 0.0, exits integer not null default 0, favourite integer not null default 0)");
        db.execSQL("create table pilot(pilot_id integer primary key, name text not null, phone text not null default '')");
        // drop favourites table
        db.execSQL("drop table favourite");
    }

    private synchronized void upgradeDatabaseToV3(SQLiteDatabase db) {
        // add "last_updated" column to takeoff table
        db.execSQL("create table takeofftmp(takeoff_id integer primary key, last_updated integer not null default current_timestamp, name text not null default '', description text not null default '', asl integer not null default 0, height integer not null default 0, latitude real not null default 0.0, latitude_cos real not null default 0.0, latitude_sin real not null default 0.0, longitude real not null default 0.0, longitude_cos real not null default 0.0, longitude_sin real not null default 0.0, exits integer not null default 0, favourite integer not null default 0)");
        db.execSQL("insert into takeofftmp select takeoff_id, current_timestamp, name, description, asl, height, latitude, latitude_cos, latitude_sin, longitude, longitude_cos, longitude_sin, exits, favourite from takeoff");
        db.execSQL("drop table takeoff");
        db.execSQL("alter table takeofftmp rename to takeoff");
    }

    private synchronized void upgradeDatabaseToV4(SQLiteDatabase db) {
        db.execSQL("alter table schedule add column pilot_id text not null default ''");
        db.execSQL("drop table pilot");
    }

    private synchronized void upgradeDatabaseToV5(SQLiteDatabase db) {
        db.execSQL("drop table schedule");
    }

    public static class ImprovedCursor {
        private Cursor cursor;

        public ImprovedCursor(Cursor cursor) {
            this.cursor = cursor;
        }

        public Cursor getCursor() {
            return cursor;
        }

        public String getString(String column) {
            int index = cursor.getColumnIndex(column);
            if (index >= 0)
                return cursor.getString(index);
            return null;
        }

        public String getStringOrThrow(String column) {
            int index = cursor.getColumnIndexOrThrow(column);
            if (index >= 0)
                return cursor.getString(index);
            return null;
        }

        public int getInt(String column) {
            int index = cursor.getColumnIndex(column);
            if (index >= 0)
                return cursor.getInt(index);
            return 0;
        }

        public int getIntOrThrow(String column) {
            int index = cursor.getColumnIndexOrThrow(column);
            if (index >= 0)
                return cursor.getInt(index);
            return 0;
        }

        public long getLong(String column) {
            int index = cursor.getColumnIndex(column);
            if (index >= 0)
                return cursor.getLong(index);
            return 0;
        }

        public long getLongOrThrow(String column) {
            int index = cursor.getColumnIndexOrThrow(column);
            if (index >= 0)
                return cursor.getLong(index);
            return 0;
        }

        public float getFloat(String column) {
            int index = cursor.getColumnIndex(column);
            if (index >= 0)
                return cursor.getFloat(index);
            return 0.0f;
        }

        public float getFloatOrThrow(String column) {
            int index = cursor.getColumnIndexOrThrow(column);
            if (index >= 0)
                return cursor.getFloat(index);
            return 0.0f;
        }

        public double getDouble(String column) {
            int index = cursor.getColumnIndex(column);
            if (index >= 0)
                return cursor.getDouble(index);
            return 0.0;
        }

        public double getDoubleOrThrow(String column) {
            int index = cursor.getColumnIndexOrThrow(column);
            if (index >= 0)
                return cursor.getDouble(index);
            return 0.0;
        }
    }
}
