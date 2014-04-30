package net.exent.flywithme.data;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Pilot;
import net.exent.flywithme.bean.Takeoff;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class Database extends SQLiteOpenHelper {
    /* NOTE: Do not use this directly, use it through getInstance() */
    private static Database databaseInstance;

    private Database(Context context) {
        super(context, "flywithme", null, 2);
    }

    @Override
    public synchronized void onCreate(SQLiteDatabase db) {
        Log.d(getClass().getName(), "onCreate()");
        createDatabaseV2(db);
        importTakeoffs(db);
    }

    @Override
    public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(getClass().getName(), "onUpgrade(" + oldVersion + ", " + newVersion + ")");
        if (oldVersion == 1) {
            createDatabaseV2(db);
            upgradeDatabaseToV2(db);
        }
    }

    public synchronized static Map<Date, Set<Pilot>> getTakeoffSchedule(Takeoff takeoff) {
        Map<Date, Set<Pilot>> schedule = new TreeMap<>();
        SQLiteDatabase db = getDatabase();
        try {
            Cursor cursor = db.query("schedule", new String[]{"timestamp", "pilot_name", "pilot_phone"}, "takeoff_id = " + takeoff.getId() + " and date(schedule.timestamp, 'unixepoch') >= date('now')", null, null, null, "timestamp");
            while (cursor.moveToNext()) {
                Date timestamp = new Date(cursor.getLong(0) * 1000); // we store timestamps in SQLite as seconds since epoch, not milliseconds (which Date use)
                String pilotName = cursor.getString(1);
                String pilotPhone = cursor.getString(2);
                Set<Pilot> pilots = schedule.get(timestamp);
                if (pilots == null) {
                    pilots = new HashSet<>();
                    schedule.put(timestamp, pilots);
                }
                pilots.add(new Pilot(pilotName, pilotPhone));
            }
            return schedule;
        } finally {
            db.close();
        }
    }

    public static synchronized List<String> getTakeoffsWithUpcomingFlights(String ignorePilot) {
        // ignorePilot is mainly used to ignore our own registrations
        // and yes, it will fail when the user change name
        List<String> takeoffs = new ArrayList<>();
        SQLiteDatabase db = getDatabase();
        try {
            Cursor cursor = db.rawQuery("select distinct takeoff.name from takeoff join schedule on takeoff.takeoff_id = schedule.takeoff_id where datetime(schedule.timestamp, 'unixepoch') >= datetime('now') and datetime(schedule.timestamp, 'unixepoch') <= datetime('now', '+2 days') and schedule.pilot_name != ?", new String[]{ignorePilot});
            while (cursor.moveToNext())
                takeoffs.add(cursor.getString(0));
            return takeoffs;
        } finally {
            db.close();
        }
    }

    public synchronized static void updateTakeoffSchedule(int takeoffId, Map<Long, List<Pilot>> schedule) {
        SQLiteDatabase db = getDatabase();
        try {
            // we'll fully replace the entries for this takeoff
            // we'll also delete old entries to clean up the database
            db.execSQL("delete from schedule where takeoff_id = " + takeoffId + " or date(timestamp, 'unixepoch', '+2 days') < date('now')");
            for (Map.Entry<Long, List<Pilot>> entry : schedule.entrySet()) {
                for (Pilot pilot : entry.getValue()) {
                    ContentValues values = new ContentValues();
                    values.put("takeoff_id", takeoffId);
                    values.put("timestamp", entry.getKey() / 1000); // date/time-functions in SQLite use seconds since epoch, not milliseconds
                    values.put("pilot_name", pilot.getName());
                    values.put("pilot_phone", pilot.getPhone());
                    db.insert("schedule", null, values);
                }
            }
        } finally {
            db.close();
        }
    }

    public synchronized static Takeoff getTakeoff(int takeoffId) {
        SQLiteDatabase db = getDatabase();
        try {
            Cursor cursor = db.query("takeoff", Takeoff.COLUMNS, "takeoff_id = " + takeoffId, null, null, null, null);
            if (cursor.moveToNext())
                return Takeoff.create(new ImprovedCursor(cursor));
            return null;
        } finally {
            db.close();
        }
    }

    public synchronized static List<Takeoff> getTakeoffs(double latitude, double longitude, int maxResult, boolean includeFavourites) {
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
        SQLiteDatabase db = getDatabase();
        try {
            Cursor cursor = db.rawQuery("select *, (select count(*) from schedule where schedule.takeoff_id = takeoff.takeoff_id and date(schedule.timestamp, 'unixepoch') = date('now')) as pilots_today from takeoff order by " + orderBy + " limit " + maxResult, null);
            while (cursor.moveToNext())
                takeoffs.add(Takeoff.create(new ImprovedCursor(cursor)));
            return takeoffs;
        } finally {
            db.close();
        }
    }

    public synchronized static void updateFavourite(Takeoff takeoff) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("favourite", takeoff.isFavourite() ? 1 : 0);
        SQLiteDatabase db = getDatabase();
        try {
            db.update("takeoff", contentValues, "takeoff_id = " + takeoff.getId(), null);
        } finally {
            db.close();
        }
    }

    private synchronized static SQLiteDatabase getDatabase() {
        if (databaseInstance == null)
            databaseInstance = new Database(FlyWithMe.getInstance());
        return databaseInstance.getWritableDatabase();
    }

    private synchronized void createDatabaseV2(SQLiteDatabase db) {
        db.execSQL("create table schedule(takeoff_id integer not null, timestamp integer not null, pilot_name text not null, pilot_phone text not null)");
        db.execSQL("create table takeoff(takeoff_id integer primary key, name text not null default '', description text not null default '', asl integer not null default 0, height integer not null default 0, latitude real not null default 0.0, latitude_cos real not null default 0.0, latitude_sin real not null default 0.0, longitude real not null default 0.0, longitude_cos real not null default 0.0, longitude_sin real not null default 0.0, exits integer not null default 0, favourite integer not null default 0)");
        db.execSQL("create table pilot(pilot_id integer primary key, name text not null, phone text not null default '')");
    }

    private synchronized void upgradeDatabaseToV2(SQLiteDatabase db) {
        importTakeoffs(db);
        // move favourites
        db.execSQL("update takeoff set favourite = 1 where takeoff_id in (select takeoff_id from favourite)");
        // drop favourites table
        db.execSQL("drop table favourite");
    }

    private synchronized void importTakeoffs(SQLiteDatabase db) {
        DataInputStream inputStream = null;
        try {
            inputStream = new DataInputStream(FlyWithMe.getInstance().getResources().openRawResource(R.raw.flywithme));
            while (true) {
                /* loop breaks once we get an EOFException */
                int takeoffId = inputStream.readShort();
                String name = inputStream.readUTF();
                String description = inputStream.readUTF();
                int asl = inputStream.readShort();
                int height = inputStream.readShort();
                float latitude = inputStream.readFloat();
                float longitude = inputStream.readFloat();
                String windpai = inputStream.readUTF();
                Takeoff takeoff = new Takeoff(takeoffId, name, description, asl, height, latitude, longitude, windpai, false);
                // NOTE: can not call updateTakeoff() here, that will cause a recursion
                ContentValues contentValues = takeoff.getContentValues();
                if (db.update("takeoff", contentValues, "takeoff_id = " + takeoff.getId(), null) <= 0) {
                    // no rows updated, insert instead
                    db.insert("takeoff", null, contentValues);
                }
            }
        } catch (EOFException e) {
            /* expected to happen */
        } catch (IOException e) {
            Log.e(getClass().getName(), "Error when reading file with takeoffs", e);
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                Log.w(getClass().getName(), "Unable to close file with takeoffs");
            }
        }
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
