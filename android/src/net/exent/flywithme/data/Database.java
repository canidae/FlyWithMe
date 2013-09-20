package net.exent.flywithme.data;

import java.util.HashSet;
import java.util.Set;

import net.exent.flywithme.bean.Takeoff;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {
	
	private static Database instance;

	private Database(Context context) {
		super(context, "flywithme", null, 1);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table favourite(takeoff_id integer primary key);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// first version, nothing to change on upgrade
	}
	
	public static Database getInstance() {
		return instance;
	}
	
	public static void init(Context context) {
		instance = new Database(context);
	}
	
	public Set<Integer> getFavourites() {
		SQLiteDatabase db = getReadableDatabase();
		Cursor cursor = db.query("favourite", new String[] {"takeoff_id"}, null, null, null, null, null);
		Set<Integer> favourites = new HashSet<Integer>();
		while (cursor.moveToNext())
			favourites.add(cursor.getInt(0));
		return favourites;
	}
	
	public void updateFavourite(Takeoff takeoff) {
		if (takeoff.isFavourite())
			getWritableDatabase().execSQL("insert or replace into favourite(takeoff_id) values (" + takeoff.getId() + ")");
		else
			getWritableDatabase().execSQL("delete from favourite where takeoff_id = " + takeoff.getId());
	}
}
