package net.exent.flywithme.data;

import java.util.ArrayList;
import java.util.List;

import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.server.flyWithMeServer.model.Pilot;
import net.exent.flywithme.server.flyWithMeServer.model.Schedule;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class Database extends SQLiteOpenHelper {
    public Database(Context context) {
        super(context, "flywithme", null, 4);
    }

    @Override
    public synchronized void onCreate(SQLiteDatabase db) {
        Log.d(getClass().getName(), "onCreate()");
        createDatabase(db);
    }

    @Override
    public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(getClass().getName(), "onUpgrade(" + oldVersion + ", " + newVersion + ")");
        if (oldVersion == 1)
            upgradeDatabaseToV2(db);
        if (oldVersion == 2)
            upgradeDatabaseToV3(db);
        if (oldVersion == 3)
            upgradeDatabaseToV4(db);
    }

    public synchronized List<Schedule> getTakeoffSchedules(Takeoff takeoff) {
        if (takeoff == null) {
            Log.w(getClass().getName(), "Unable to get takeoff schedules, argument is null");
            return null;
        }
        List<Schedule> schedules = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        if (db == null)
            throw new IllegalArgumentException("Unable to get database object");
        try {
            Cursor cursor = db.query("schedule", new String[]{"timestamp", "pilot_name", "pilot_phone", "pilot_id"}, "takeoff_id = " + takeoff.getId() + " and date(schedule.timestamp, 'unixepoch') >= date('now', '-24 hour')", null, null, null, "timestamp");
            while (cursor.moveToNext()) {
                long timestamp = cursor.getLong(0);
                Schedule schedule = null;
                for (Schedule tmpSchedule : schedules) {
                    if (tmpSchedule.getTimestamp() == timestamp) {
                        schedule = tmpSchedule;
                        break;
                    }
                }
                if (schedule == null) {
                    schedule = new Schedule();
                    schedules.add(schedule);
                }
                schedule.setTimestamp(timestamp);
                String pilotName = cursor.getString(1);
                String pilotPhone = cursor.getString(2);
                String pilotId = cursor.getString(3);
                List<Pilot> pilots = schedule.getPilots();
                if (pilots == null) {
                    pilots = new ArrayList<>();
                    schedule.setPilots(pilots);
                }
                Pilot pilot = new Pilot();
                pilot.setName(pilotName);
                pilot.setPhone(pilotPhone);
                pilot.setId(pilotId);
                pilots.add(pilot);
            }
            cursor.close();
            return schedules;
        } finally {
            db.close();
        }
    }

    public synchronized void updateSchedules(List<Schedule> schedules) {
        if (schedules == null) {
            Log.w(getClass().getName(), "Unable to update schedules, argument is null");
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        if (db == null)
            throw new IllegalArgumentException("Unable to get database object");
        try {
            db.execSQL("delete from schedule");
            for (Schedule schedule : schedules) {
                for (Pilot pilot : schedule.getPilots()) {
                    ContentValues values = new ContentValues();
                    values.put("takeoff_id", schedule.getTakeoffId());
                    values.put("timestamp", schedule.getTimestamp());
                    values.put("pilot_name", pilot.getName());
                    values.put("pilot_phone", pilot.getPhone());
                    values.put("pilot_id", pilot.getId());
                    db.insert("schedule", null, values);
                }
            }
        } finally {
            db.close();
        }
    }

    public synchronized Takeoff getTakeoff(long takeoffId) {
        SQLiteDatabase db = getReadableDatabase();
        if (db == null)
            throw new IllegalArgumentException("Unable to get database object");
        Cursor cursor = null;
        try {
            cursor = db.query("takeoff", Takeoff.COLUMNS, "takeoff_id = " + takeoffId, null, null, null, null);
            if (cursor.moveToNext())
                return Takeoff.create(new ImprovedCursor(cursor));
            return null;
        } finally {
            if (cursor != null)
                cursor.close();
            db.close();
        }
    }

    public synchronized void updateTakeoff(Takeoff takeoff) {
        if (takeoff == null) {
            Log.w(getClass().getName(), "Unable to update takeoff, argument is null");
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        if (db == null)
            throw new IllegalArgumentException("Unable to get database object");
        try {
            ContentValues contentValues = takeoff.getContentValues();
            if (db.update("takeoff", contentValues, "takeoff_id = " + takeoff.getId(), null) <= 0) {
                // no rows updated, insert instead
                db.insert("takeoff", null, contentValues);
            }
        } finally {
            db.close();
        }
    }

    public synchronized List<Takeoff> getTakeoffs(double latitude, double longitude, int maxResult, boolean includeFavourites) {
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
        SQLiteDatabase db = getReadableDatabase();
        if (db == null)
            throw new IllegalArgumentException("Unable to get database object");
        try {
            Cursor cursor = db.rawQuery("select *, (select count(*) from schedule where schedule.takeoff_id = takeoff.takeoff_id and date(schedule.timestamp, 'unixepoch') = date('now')) as pilots_today, (select count(*) from schedule where schedule.takeoff_id = takeoff.takeoff_id and date(schedule.timestamp, 'unixepoch') > date('now')) as pilots_later from takeoff order by " + orderBy + " limit " + maxResult, null);
            while (cursor.moveToNext())
                takeoffs.add(Takeoff.create(new ImprovedCursor(cursor)));
            cursor.close();
            return takeoffs;
        } finally {
            db.close();
        }
    }

    public synchronized void updateFavourite(Takeoff takeoff) {
        if (takeoff == null) {
            Log.w(getClass().getName(), "Unable to update takeoff, argument is null");
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        if (db == null)
            throw new IllegalArgumentException("Unable to get database object");
        ContentValues contentValues = new ContentValues();
        contentValues.put("favourite", takeoff.isFavourite() ? 1 : 0);
        try {
            db.update("takeoff", contentValues, "takeoff_id = " + takeoff.getId(), null);
        } finally {
            db.close();
        }
    }

    /**
     * Create the most recent database layout.
     * This is only called when there's no existing database on device (fresh install).
     * @param db The SQLite database.
     */
    private synchronized void createDatabase(SQLiteDatabase db) {
        db.execSQL("create table schedule(takeoff_id integer not null, timestamp integer not null, pilot_name text not null, pilot_phone text not null)");
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
