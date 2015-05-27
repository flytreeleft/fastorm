package org.yelsky.fastorm.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.yelsky.fastorm.R;
import org.yelsky.fastorm.Session;
import org.yelsky.fastorm.SessionFactory;
import org.yelsky.fastorm.impl.EntityMapInfo;
import org.yelsky.fastorm.impl.EntityResolver;
import org.yelsky.fastorm.impl.SessionImp;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;


public class Test extends Activity {
	
	ListView mMsgs;
	ArrayAdapter mMsgAdapter;
	private static final String TAG = "TEST";
	
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);
//		mMsgs = (ListView)findViewById(R.id.listMsg);
//		mMsgAdapter = new ArrayAdapter(this, R.layout.item, R.id.itemName);
		mMsgs.setAdapter(mMsgAdapter);
		testAndroidJsonParse();
		try {
			testJacksonParser();
		} catch (Exception e1) {
			Log.e(TAG, e1.getMessage(), e1);
		}

		EntityMapInfo emi = EntityResolver.resolve(Line.class);
		Log.d(TAG, String.format("table=%s, id=%s", emi.table, emi.idFieldInfo.columnName));
		
		SQLiteOpenHelper openHelper = new DatabaseHelper(this);
		SQLiteDatabase db = openHelper.getWritableDatabase();

		ArrayList<Class> cls = new ArrayList<Class>();
		cls.add(Line.class);
		cls.add(Team.class);
		final Session s = (SessionImp) SessionFactory.getFactory(db)
				.getSession(cls);
		String lineJson = "{ \"name\": \"Line 1\", \"number\": 1, \"updatedate\": \"Tue Nov 04 20:14:11 EST 2003\" } ";
		
		
		try {
			testFromJson(s, lineJson);
			testInsert(s);
			testFindAll(s);
			testMultithread(s);
		} catch (Exception e) {
			Log.e(TAG, e.getLocalizedMessage(), e);
		}
	}

	private void testFromJson(final Session s, String lineJson)
			throws Exception {
		mMsgAdapter.add("test parsing object from json");
		Line l = s.fromJson(lineJson, Line.class);
		assert (l.name == "Line 1");
		assert (l.number == 1);
		assert (l.updatedate.equals(new java.util.Date("Tue Nov 04 20:14:11 EST 2003")));
		String lineJsonArray = "{\"root\":{ \"lines\":[{ \"name\": \"Line 1\", \"number\": 1, \"updatedate\": \"Tue Nov 04 20:14:11 EST 2003\" } ]}}";
		
		List<Line> ls = s.listFromJson(lineJsonArray,"/root/lines", Line.class);
		lineJson = "{ \"line\" : { \"name\": \"Line 1\", \"number\": 1, \"updatedate\": \"Tue Nov 04 20:14:11 EST 2003\" } }";
		
		l = s.fromJson(lineJson, "/line", Line.class);
		assert (l.name == "Line 1");
		assert (l.number == 1);
		assert (l.updatedate.equals(new java.util.Date("Tue Nov 04 20:14:11 EST 2003")));
		String json = getJson();
		long stt =System.currentTimeMillis();
		List<Station> stations = s.listFromJson(json, "/stations", Station.class);
		System.out.println("takes "+(System.currentTimeMillis()-stt));
		System.out.println("get "+stations.size() +" stations");
		for(Station st: stations){
			System.out.println("station_id= "+st.station_id);
			System.out.println("station_name= "+st.station_name);
		}
	}

	private String getJson() throws IOException {
		InputStream is = getResources().openRawResource(R.raw.test);
		StringBuffer sb = new StringBuffer();
		int c;
		while ( (c=is.read())!=-1){
			sb.append((char)c);
		}
		return sb.toString();
	}

	private void testJacksonParser() throws JsonParseException, IOException {
		mMsgAdapter.add("test Jackson Json parser parsing performance");
		
		JsonFactory f = new JsonFactory();
		// [JACKSON-259]: ability to suppress canonicalization
		f.disable(JsonParser.Feature.CANONICALIZE_FIELD_NAMES);
		InputStream is = this.getResources().openRawResource(R.raw.test);
		String json = getJson();
		long st = System.currentTimeMillis();
		JsonParser jp = f.createJsonParser(json);
		jp.enable(JsonParser.Feature.ALLOW_COMMENTS);

		JsonToken t;
		
		while ((t = jp.nextToken()) != null) {
			//Log.d(TAG, "Token: " + t);
			if (t == JsonToken.FIELD_NAME) {
				String name = jp.getCurrentName();
				int ix = name.indexOf('\0');
				if (ix >= 0) {
					throw new RuntimeException("Null byte for name, at index #"
							+ ix);
				}
			} else if (t.toString().startsWith("VALUE")) {
				if (t == JsonToken.VALUE_STRING) {
				}
			}
		}
		mMsgAdapter.add(String.format("Jackson parser takes %dms", (System.currentTimeMillis()-st)));
		jp.close();
	}

	private void testAndroidJsonParse() {
		mMsgAdapter.add("test Android default Json parser parsing performance");
		try {
			String json = getJson();
			long st = System.currentTimeMillis();
			JSONObject obj = (JSONObject) new JSONTokener(json)
					.nextValue();
			// Check for error message from portal
			JSONArray stations = obj.getJSONArray("stations");
			// Insert the station into database
			int mNumStations = stations.length();
			Uri uri = null;
			for (int i = 0; i < mNumStations; i++) {
				// Post update message if we have messenger
				JSONObject station = stations.getJSONObject(i);
				JSONObject logo = station.getJSONObject("logo");

				// Station & logo metadata
				String imageId = null;
				if (logo.length() > 0) {
					// Insert the image into database
				}
			}
			mMsgAdapter.add(String.format("Default parser takes %dms", (System.currentTimeMillis()-st)));
		}  catch (Exception e) {
			Log.e(TAG, e.getLocalizedMessage(),e);
		}

	}

	private void testFindAll(final Session s) throws Exception {
		mMsgAdapter.add("test find all");
		long st;
		st = System.currentTimeMillis();
		Line l = s.find(Line.class, 1);
		Log.d(TAG, String.format("find Lines %s, %d, %s", l.name, l.number,
				l.updatedate.toGMTString()));
		List<Line> ls = s.findAll(Line.class);
		mMsgAdapter.add(String.format("finds %d records, takes %dms", ls.size(), (System.currentTimeMillis()-st)));
	}

	private void testMultithread(final Session s) throws Exception {
		mMsgAdapter.add("test multithread environments");
		s.query(LineQueryTest.class, "queryById", 100, "test line");
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Random r = new Random();
					for (int i = 0; i < 100; i++) {
						final Line o = new Line();
						o.name = "test line";
						o.number = 1;
						Thread.sleep(r.nextInt(10));
						runOnUiThread(new Runnable(){
							@Override
							public void run() {
								mMsgAdapter.add( "" + Thread.currentThread().toString()
										+ " executes insert");
							}
						});
						s.insert(o);
					}
				} catch (Exception e) {
					Log.e(TAG, e.getLocalizedMessage(), e);
				}
			}
		});
		t.start();

		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Random r = new Random();
					for (int i = 0; i < 100; i++) {
						Thread.sleep(r.nextInt(10));
						runOnUiThread(new Runnable(){
							@Override
							public void run() {
								mMsgAdapter.add( "" + Thread.currentThread().toString()
										+ " executes find");
							}
						});
						s.find(Line.class, r.nextInt(1000));
					}
				} catch (Exception e) {
					Log.e(TAG, e.getLocalizedMessage(), e);
				}

			}
		});
		t2.start();

		Thread t3 = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Random r = new Random();
					for (int i = 0; i < 100; i++) {
						Thread.sleep(r.nextInt(10));
						runOnUiThread(new Runnable(){
							@Override
							public void run() {
								mMsgAdapter.add( "" + Thread.currentThread().toString()
										+ " executes find");
							}
						});
						s.find(Line.class, r.nextInt(1000));
					}
				} catch (Exception e) {
					Log.e(TAG, e.getLocalizedMessage(), e);
				}

			}
		});
		t3.start();
	}

	private void testInsert(final Session s) throws Exception {
		mMsgAdapter.add("test insert");
		final Line o = new Line();
		o.name = "test line";
		o.number = 10;
		o.updatedate = new java.util.Date();
		long st = System.currentTimeMillis();
		int i = 0;
		int NUM = 10;
		for (; i < NUM; i++){
			Line l = new Line();
			l.name = "test line";
			l.number = 10;
			l.updatedate = new java.util.Date();
			s.insert(l);
		}
		s.commit();
		mMsgAdapter.add( String.format("insert %d records with cache takes %dms", i,
				(System.currentTimeMillis() - st)));
		st = System.currentTimeMillis();
	}
}