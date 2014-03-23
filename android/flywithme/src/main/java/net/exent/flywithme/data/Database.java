package net.exent.flywithme.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.exent.flywithme.bean.Takeoff;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {
    private static Database instance;

    private Database(Context context) {
        super(context, "flywithme", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createDatabaseV1(db);
        upgradeDatabaseToV2(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2)
            upgradeDatabaseToV2(db);
    }

    public static void init(Context context) {
        instance = new Database(context);
    }

    public static Map<Date, List<String>> getTakeoffSchedule(Takeoff takeoff) {
        Map<Date, List<String>> schedule = new TreeMap<>();
        SQLiteDatabase db = instance.getReadableDatabase();
        if (db == null)
            return schedule;
        Cursor cursor = db.query("schedule", new String[] {"timestamp", "pilot"}, "takeoff_id = " + takeoff.getId(), null, null, null, "timestamp");
        while (cursor.moveToNext()) {
            Date timestamp = new Date((long) cursor.getInt(0) * 1000L); // int * int = int. we want long, hence the cast & "L"
            String pilot = cursor.getString(1);
            if (schedule.containsKey(timestamp))
                schedule.get(timestamp).add(pilot);
            else
                schedule.put(timestamp, new ArrayList<>(Arrays.asList(pilot)));
        }
        return schedule;
    }

    public static void updateTakeoffSchedule(int takeoffId, Map<Date, List<String>> schedule) {
        // TODO: how will we handle the current user's registration? we need to store the id of the user, could just match that with the database, it's not unique, though, hmm, bad idea?
        // TODO: let's just save name/phone, timestamp and takeoffId for user like we save preferences
        SQLiteDatabase db = instance.getWritableDatabase();
        if (db == null)
            return;
        // we'll fully replace the entries for this takeoff
        db.execSQL("delete from schedule where takeoff_id = " + takeoffId);
        for (Map.Entry<Date, List<String>> entry : schedule.entrySet()) {
            for (String pilot : entry.getValue()) {
                ContentValues values = new ContentValues();
                values.put("takeoff_id", takeoffId);
                values.put("timestamp", entry.getKey().getTime() / 1000);
                values.put("pilot", pilot);
                db.insert("schedule", null, values);
            }
        }
    }

    public static Set<Integer> getFavourites() {
        Set<Integer> favourites = new HashSet<>();
        SQLiteDatabase db = instance.getReadableDatabase();
        if (db == null)
            return favourites;
        Cursor cursor = db.query("favourite", new String[] {"takeoff_id"}, null, null, null, null, null);
        while (cursor.moveToNext())
            favourites.add(cursor.getInt(0));
        return favourites;
    }

    public static void updateFavourite(Takeoff takeoff) {
        SQLiteDatabase db = instance.getWritableDatabase();
        if (db == null)
            return;
        if (takeoff.isFavourite())
            db.execSQL("insert or replace into favourite(takeoff_id) values (" + takeoff.getId() + ")");
        else
            db.execSQL("delete from favourite where takeoff_id = " + takeoff.getId());
    }

    private void createDatabaseV1(SQLiteDatabase db) {
        db.execSQL("create table favourite(takeoff_id integer primary key)");
    }

    private void upgradeDatabaseToV2(SQLiteDatabase db) {
        db.execSQL("create table schedule(takeoff_id integer not null, timestamp integer not null, pilot text not null)");
    }
}
