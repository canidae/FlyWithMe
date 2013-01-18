package net.exent.flywithme.dao;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.data.Takeoff;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.util.Log;

public class Flightlog extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "flywithme.db";
	//private boolean doCrawl = false;
	
	public Flightlog(Context context) {
		super(context, DATABASE_NAME, null, 1);
		/* Why this weird & clumsy approach?
		 * 1. Because you seemingly can't open a supplied (READONLY) database, you must copy it somewhere first(!).
		 * 2. The idea was to somewhere in the future make it possible to have the app update the database on its own.
		 * 3. Removing the supplied database and wiping the user data for the application will force crawling.
		 */
		Log.i("Flightlog", "Checking if internal database exists");
		File internalDatabaseFile = context.getDatabasePath(DATABASE_NAME);
		if (internalDatabaseFile.exists())
			return;
		
		Log.i("Flightlog", "Unable to find internal database, trying to copy supplied database");
		/* database does not exist, copy it from assets */
		try {
			// create missing "database" directory upon first install
			if (!internalDatabaseFile.getParentFile().exists())
				internalDatabaseFile.getParentFile().mkdir();
			InputStream inputStream = context.getResources().openRawResource(R.raw.flywithme);
			OutputStream outputStream = new FileOutputStream(internalDatabaseFile);
			int length;
			byte[] buffer = new byte[1024];
			while ((length = inputStream.read(buffer, 0, buffer.length)) != -1) {
	            outputStream.write(buffer, 0, length);
	        }
	        outputStream.close();
	        inputStream.close();
		} catch (FileNotFoundException e2) {
			/* no database supplied, we'll need to crawl */
			//doCrawl = true;
			Log.w("Flightlog", "No database supplied, have to crawl, will take some time", e2);
			throw new RuntimeException("AAAAAH");
		} catch (IOException e2) {
			Log.e("Flightlog", "Error copying database", e2);
			throw new RuntimeException("AAAAAH");
		}
	}

	@Override
	public void onCreate(SQLiteDatabase database) {
		Log.i("Flightlog", "Creating database");
		database.execSQL("CREATE TABLE takeoff(id INTEGER PRIMARY KEY, name TEXT, description TEXT, asl INTEGER, height INTEGER, latitude REAL, longitude REAL)");
		crawl(database);
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
	}
	
	public List<Takeoff> getTakeoffs(double maxDegrees) {
		SQLiteDatabase database = getReadableDatabase();
		final Location loc = FlyWithMe.getLocation();
		String where = "ABS(latitude - " + loc.getLatitude() + ") < " + maxDegrees + " AND ABS(longitude - " + loc.getLongitude() + ") < " + maxDegrees;
		Cursor cursor = database.query(false, "takeoff", new String[] {"id AS _id", "name", "description", "asl", "height", "latitude", "longitude"}, where, null, null, null, null, null);
		List<Takeoff> takeoffs = new ArrayList<Takeoff>();
		while (cursor.moveToNext())
			takeoffs.add(new Takeoff(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getInt(4), cursor.getFloat(5), cursor.getFloat(6)));
		cursor.close();
		
		/* sorting by distance */
		Collections.sort(takeoffs, new Comparator<Takeoff>() {
			public int compare(Takeoff lhs, Takeoff rhs) {
				if (loc.distanceTo(lhs.getLocation()) > loc.distanceTo(rhs.getLocation()))
					return 1;
				else if (loc.distanceTo(lhs.getLocation()) < loc.distanceTo(rhs.getLocation()))
					return -1;
				return 0;
			}
		});
		
		return takeoffs;
	}

	/*
	 * http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=4
	 * we can set "country_id" to a fixed value, it only means that wrong country will be displayed (which we don't care about)
	 */
	public void crawl(SQLiteDatabase database) {
		Log.i("Flightlog", "crawling");
		SQLiteStatement addTakeoff = database.compileStatement("INSERT OR REPLACE INTO takeoff(id, name, description, asl, height, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?, ?)");
		int takeoff = 0;
		int lastValidTakeoff = 0;
		boolean tryAgain = true;
		while (takeoff++ < lastValidTakeoff + 50) { // when we haven't found a takeoff within the last 50 fetches from flightlog, assume all is found
			try {
				URL url = new URL("http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=" + takeoff);
				HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
				switch (httpUrlConnection.getResponseCode()) {
				case HttpURLConnection.HTTP_OK:
					String charset = getCharsetFromHeaderValue(httpUrlConnection.getContentType());
					StringBuilder sb = new StringBuilder();
					BufferedReader br = new BufferedReader(new InputStreamReader(httpUrlConnection.getInputStream(), charset), 32768);
					char[] buffer = new char[32768];
					int read;
					while ((read = br.read(buffer)) != -1)
						sb.append(buffer, 0, read);
					br.close();
					
					String text = sb.toString();
					Pattern namePattern = Pattern.compile(".*<title>.* - .* - .* - (.*)</title>.*", Pattern.DOTALL);
					Matcher nameMatcher = namePattern.matcher(text);
					Pattern descriptionPattern = Pattern.compile(".*Description</td>.*('right'>|'left'></a>)(.*)</td></tr>.*Coordinates</td>.*", Pattern.DOTALL);
					Matcher descriptionMatcher = descriptionPattern.matcher(text);
					Pattern altitudePattern = Pattern.compile(".*Altitude</td><td bgcolor='white'>(\\d+) meters asl Top to bottom (\\d+) meters</td>.*", Pattern.DOTALL);
					Matcher altitudeMatcher = altitudePattern.matcher(text);
					Pattern coordPattern = Pattern.compile(".*Coordinates</td>.*DMS: ([NS]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039; &nbsp;([EW]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039;.*", Pattern.DOTALL);
					Matcher coordMatcher = coordPattern.matcher(text);
					
					if (nameMatcher.matches() && coordMatcher.matches()) {
						String takeoffName = nameMatcher.group(1).trim();
						String description = "";
						if (descriptionMatcher.matches())
							description = descriptionMatcher.group(2).replace("<br />", "").trim();
						int aboveSeaLevel = 0;
						int height = 0;
						if (altitudeMatcher.matches()) {
							aboveSeaLevel = Integer.parseInt(altitudeMatcher.group(1).trim());
							height = Integer.parseInt(altitudeMatcher.group(2).trim());
						}
	
						String northOrSouth = coordMatcher.group(1);
						int latDeg = Integer.parseInt(coordMatcher.group(2));
						int latMin = Integer.parseInt(coordMatcher.group(3));
						int latSec = Integer.parseInt(coordMatcher.group(4));
						float latitude = 0;
						latitude = (float) latDeg + (float) (latMin * 60 + latSec) / (float) 3600;
						if ("S".equals(northOrSouth))
							latitude *= -1.0;
	
						String eastOrWest = coordMatcher.group(5);
						int lonDeg = Integer.parseInt(coordMatcher.group(6));
						int lonMin = Integer.parseInt(coordMatcher.group(7));
						int lonSec = Integer.parseInt(coordMatcher.group(8));
						float longitude = 0;
						longitude = (float) lonDeg + (float) (lonMin * 60 + lonSec) / (float) 3600;
						if ("W".equals(eastOrWest))
							longitude *= -1.0;
	
						Log.i("Flightlog", "Adding takeoff: " + takeoff + ", " + takeoffName + ", " + description + ", " + aboveSeaLevel + ", " + height + ", " + latitude + ", " + longitude);
						addTakeoff.bindLong(1, takeoff);
						addTakeoff.bindString(2, takeoffName);
						addTakeoff.bindString(3, description);
						addTakeoff.bindLong(4, aboveSeaLevel);
						addTakeoff.bindLong(5, height);
						addTakeoff.bindDouble(6, latitude);
						addTakeoff.bindDouble(7, longitude);
						addTakeoff.executeInsert();
						lastValidTakeoff = takeoff;
					}
					break;
	
				default:
					Log.w("Flightlog", "Whoops, not good! Response code " + httpUrlConnection.getResponseCode() + " when fetching takeoff with ID " + takeoff);
					break;
				}
				tryAgain = true;
			} catch (Exception e) {
				/* try one more time if we get an exception */
				if (tryAgain)
					--takeoff;
				else
					Log.w("Flightlog", "Exception when trying to fetch takeoff with ID " + takeoff, e);
				tryAgain = false;
			}
		}
	}
	
	private static String getCharsetFromHeaderValue(String text) {
        int start = text.indexOf("charset=");
        if (start >= 0) {
            start += 8;
            int end = text.indexOf(";", start);
            int pos = text.indexOf(" ", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            pos = text.indexOf("\n", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            if (end == -1)
                end = text.length();
            if (text.charAt(start) == '"' && text.charAt(end - 1) == '"') {
                ++start;
                --end;
            }
            return text.substring(start, end);
        }
        return "iso-8859-1";
	}
}
