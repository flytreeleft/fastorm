package org.yelsky.fastorm.impl;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.yelsky.fastorm.ResultCallback;
import org.yelsky.fastorm.Session;
import org.yelsky.fastorm.annotation.Order;
import org.yelsky.fastorm.annotation.Param;
import org.yelsky.fastorm.annotation.Query;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class SessionImp implements Session {
	private static final String TAG = "SessionImp";
	private Map<Class, EntityMapInfo> clsMaps = new Hashtable<Class, EntityMapInfo>();

	private SQLiteDatabase db;
	private volatile boolean mInTrans = false;

	class ObjStateNode {
		Object obj;
		ObjectState state;

		ObjStateNode(Object o, ObjectState s) {
			obj = o;
			state = s;
		}
	}

	private ArrayList<ObjStateNode> mDirtyObjs = new ArrayList<ObjStateNode>();

	private Map<String, Integer> mTblSeqs = new HashMap<String, Integer>();
	private ReentrantReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
	private final Lock mReadLock = mReadWriteLock.readLock();
	private final Lock mWriteLock = mReadWriteLock.writeLock();

	private static final int THRESHHOLD = 100;

	enum ObjectState {
		SANE, NEW, DIRTY, REMOVE
	}

	public SessionImp(SQLiteDatabase db) {
		this.db = db;
		Cursor c = db
				.rawQuery(
						"select name from sqlite_master where type='table' or type='view'",
						null);
		while (c.moveToNext()) {
			String tbl = c.getString(0);
			// sqlite bug table sometimes is null
			if (tbl == null || "null".equals(tbl))
				continue;
			Cursor c2 = db.rawQuery("select max(oid) from " + tbl, null);
			c2.moveToNext();
			int seq = c2.getInt(0);
			c2.close();
			mTblSeqs.put(tbl.toLowerCase(), seq);
			Log.d(TAG, String.format("%s max seq is %d", tbl, seq));
		}
		c.close();
	}

	@Override
	public void close() throws Exception {

	}

	public static void setFieldValue(final Field field, final Object object,
			final Object value) {
		try {
			boolean acessible = field.isAccessible();
			field.setAccessible(true);
			field.set(object, value);
			field.setAccessible(acessible);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setFieldValue(final Object o, final Field field,
			final FieldType type, String columnName, Cursor cursor) {
		int idx = cursor.getColumnIndex(columnName);
		setFieldValue(o, field, type, cursor, idx);
	}

	private static void setFieldValue(final Object o, final Field field,
			final FieldType type, Cursor cursor, int idx) {
		if (type == FieldType.STRING) {
			setFieldValue(field, o, cursor.getString(idx));
		} else if (type == FieldType.BYTE) {
			setFieldValue(field, o, (byte) cursor.getShort(idx));
		} else if (type == FieldType.CHAR) {
			String v = cursor.getString(idx);
			if (v != null && v.length() > 0)
				setFieldValue(field, o, v.charAt(0));
		} else if (type == FieldType.BOOLEAN) {
			Short v = cursor.getShort(idx);
			if (v > 0)
				setFieldValue(field, o, Boolean.TRUE);
			else
				setFieldValue(field, o, Boolean.FALSE);
		} else if (type == FieldType.SHORT) {
			setFieldValue(field, o, cursor.getShort(idx));
		} else if (type == FieldType.INT) {
			setFieldValue(field, o, cursor.getInt(idx));
		} else if (type == FieldType.LONG) {
			setFieldValue(field, o, cursor.getLong(idx));
		} else if (type == FieldType.FLOAT) {
			setFieldValue(field, o, cursor.getFloat(idx));
		} else if (type == FieldType.DOUBLE) {
			setFieldValue(field, o, cursor.getDouble(idx));
		} else if (type == FieldType.BLOB) {
			setFieldValue(field, o, cursor.getBlob(idx));
		} else if (type == FieldType.DATE) {
			String sd = cursor.getString(idx);
			if (sd != null) {
				java.util.Date date = new java.util.Date(sd);
				setFieldValue(field, o, date);
			}
		}
	}

	@Override
	public <T> T find(Class<T> clazz, Object primaryKey) throws Exception {
		checkDirty();
		mReadLock.lock();
		try {

			long t = System.currentTimeMillis();
			EntityMapInfo emi = null;
			if (!clsMaps.containsKey(clazz)) {
				emi = EntityResolver.resolve(clazz);
				clsMaps.put(clazz, emi);
			} else
				emi = clsMaps.get(clazz);

			Cursor cursor = db.rawQuery(
					"select * from " + emi.table + " where "
							+ emi.idFieldInfo.columnName + "=" + primaryKey,
					null);
			T o = clazz.newInstance();
			if (cursor.moveToFirst()) {
				Iterator i = emi.fieldsMap.keySet().iterator();
				while (i.hasNext()) {
					Field f = (Field) i.next();
					FieldMapInfo fi = emi.fieldsMap.get(f);
					setFieldValue(o, f, fi.type, fi.columnName, cursor);
				}
				emi.idField.set(o, primaryKey);
			}
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
			Log.d(TAG, String
					.format("----%d", (System.currentTimeMillis() - t)));
			return o;
		} finally {
			mReadLock.unlock();
		}
	}

	private void checkDirty() throws Exception {
		mWriteLock.lock();
		try {
			if (mDirtyObjs.size() > 0) {
				// here we must spin up our lock to exclusive lock to flush
				// dirty
				commit();
			}
		} finally {
			mWriteLock.unlock();
		}

	}

	@Override
	public void remove(Object object) throws Exception {
		ensureIsPersistentable(object);
		mWriteLock.lock();
		try {
			mDirtyObjs.add(new ObjStateNode(object, ObjectState.REMOVE));
		} finally {
			mWriteLock.unlock();
		}
	}

	void _remove(Object o) throws Exception {
		EntityMapInfo emi = getEntityMapInfo(o);
		StringBuffer sb = new StringBuffer();
		sb.append("delete from ");
		sb.append(emi.table);
		sb.append(" where ");
		sb.append(emi.idFieldInfo.columnName);
		sb.append("=");
		sb.append(emi.idField.get(o));
		Log.d(TAG, sb.toString());
		db.execSQL(sb.toString());
	}

	@Override
	public void update(Object object) throws Exception {
		ensureIsPersistentable(object);
		mWriteLock.lock();
		try {
			mDirtyObjs.add(new ObjStateNode(object, ObjectState.DIRTY));
		} finally {
			mWriteLock.unlock();
		}
	}

	void _update(Object o) throws Exception {
		EntityMapInfo emi = getEntityMapInfo(o);
		StringBuffer sb = new StringBuffer();
		sb.append("update ");
		sb.append(emi.table);
		sb.append(" set ");
		Iterator i = emi.fieldsMap.keySet().iterator();
		while (i.hasNext()) {
			Field f = (Field) i.next();
			FieldMapInfo fi = emi.fieldsMap.get(f);
			sb.append(fi.columnName);
			sb.append("=");
			Object value = f.get(o);
			if (fi.type == FieldType.STRING || fi.type == FieldType.CHAR) {
				sb.append("'");
				sb.append(value);
				sb.append("'");
			} else if (fi.type == FieldType.BOOLEAN) {
				String v = Boolean.TRUE.equals(value) ? "1" : "0";
				sb.append(v);
			} else if (fi.type == FieldType.BYTE || fi.type == FieldType.SHORT
					|| fi.type == FieldType.INT || fi.type == FieldType.LONG
					|| fi.type == FieldType.FLOAT
					|| fi.type == FieldType.DOUBLE) {
				sb.append(value);
			} else if (fi.type == FieldType.DATE) {
				sb.append("'" + value + "'");
			}
			sb.append(",");
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(" where ");
		sb.append(emi.idFieldInfo.columnName);
		sb.append("=");
		sb.append(emi.idField.get(o));
		Log.d(TAG, sb.toString());
		db.execSQL(sb.toString());

		// TODO if has byte[] field, use cursor to update it

	}

	@Override
	public void insert(Object object) throws Exception {
		EntityMapInfo emi = ensureIsPersistentable(object);
		mWriteLock.lock();
		try {
			Object io = object;
			if (emi.idField != null) {
				Integer seq = mTblSeqs.get(emi.table.toLowerCase());
				seq++;
				Log.d(TAG, "SEQ=" + seq);
				mTblSeqs.put(emi.table.toLowerCase(), seq);
				if (emi.idFieldInfo.type == FieldType.SHORT) {
					setFieldValue(emi.idField, object, (short) seq.intValue());
				} else if (emi.idFieldInfo.type == FieldType.INT) {
					setFieldValue(emi.idField, object, seq);
				} else if (emi.idFieldInfo.type == FieldType.LONG) {
					setFieldValue(emi.idField, object, (long) seq.intValue());
				}
				setFieldValue(emi.idField, object, seq);
			}
			mDirtyObjs.add(new ObjStateNode(io, ObjectState.NEW));
			if (mDirtyObjs.size() > THRESHHOLD) {
				commit();
			}
		} finally {
			mWriteLock.unlock();
		}
	}

	private Object _dup(Object o) throws IllegalAccessException,
			InstantiationException {
		Object clone = o.getClass().newInstance();
		Field[] fields = o.getClass().getDeclaredFields();
		for (Field f : fields) {
			f.set(clone, f.get(o));
		}
		return clone;
	}

	void _insert(Object object) throws Exception {
		EntityMapInfo emi = null;
		Class cls = object.getClass();
		if (!clsMaps.containsKey(cls)) {
			emi = EntityResolver.resolve(cls);
			clsMaps.put(cls, emi);
		} else
			emi = clsMaps.get(cls);
		StringBuffer sb = new StringBuffer();
		sb.append("insert into " + emi.table + " (");
		if (emi.idField != null) {
			sb.append(emi.idFieldInfo.columnName);
			sb.append(",");
		}
		List<Field> columns = new ArrayList<Field>();
		Iterator<Field> itr = emi.fieldsMap.keySet().iterator();
		while (itr.hasNext()) {
			Field f = itr.next();
			Object value = f.get(object);
			if (value != null) {
				columns.add(f);
			}
		}

		for (int i = 0; i < columns.size(); i++) {
			Field f = columns.get(i);
			sb.append(emi.fieldsMap.get(f).columnName);
			sb.append(",");
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(") values(");
		if (emi.idField != null) {
			sb.append(emi.idField.get(object));
			sb.append(",");
		}

		for (int i = 0; i < columns.size(); i++) {
			Field f = columns.get(i);
			FieldType t = emi.fieldsMap.get(f).type;
			Object v = f.get(object);

			if (t == FieldType.STRING || t == FieldType.CHAR) {
				sb.append("'");
				sb.append(v);
				sb.append("'");
			} else if (t == FieldType.BOOLEAN) {
				String sv = Boolean.TRUE.equals(v) ? "1" : "0";
				sb.append(sv);
			} else if (t == FieldType.BYTE || t == FieldType.SHORT
					|| t == FieldType.INT || t == FieldType.LONG
					|| t == FieldType.FLOAT || t == FieldType.DOUBLE) {
				sb.append(v);
			} else if (t == FieldType.DATE) {
				sb.append("'" + v + "'");
			}
			sb.append(",");
			Log.d(TAG, String.format("column: %s=%s", f.getName(),
					v == null ? "null" : v.toString()));
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(")");
		Log.d(TAG, sb.toString());
		// long t = System.currentTimeMillis();
		db.execSQL(sb.toString());
		// TODO for byte[] field
	}

	@Override
	public void beginTrans() throws Exception {
		mInTrans = true;
	}

	@Override
	public void endTrans() throws Exception {
		mInTrans = false;
		commit();
	}

	@Override
	public void commit() throws Exception {
		Log.d(TAG, "commit");
		mWriteLock.lock();
		db.beginTransaction();
		long st = System.currentTimeMillis();
		try {
			Log.d(TAG, "committing....");
			Iterator<ObjStateNode> i = mDirtyObjs.iterator();
			while (i.hasNext()) {
				ObjStateNode osn = i.next();
				if (osn.state == ObjectState.NEW)
					_insert(osn.obj);
				else if (osn.state == ObjectState.REMOVE)
					_remove(osn.obj);
				else if (osn.state == ObjectState.DIRTY)
					_update(osn.obj);
			}
			db.setTransactionSuccessful();
			evictDirty();
			Log.d(TAG, String.format("commit takes %dms", (System
					.currentTimeMillis() - st)));
		} finally {
			mWriteLock.unlock();
			db.endTransaction();
		}
	}

	@Override
	public void evictDirty() throws Exception {
		mWriteLock.lock();
		try {
			mDirtyObjs.clear();
		} finally {
			mWriteLock.unlock();
		}
	}

	private EntityMapInfo ensureIsPersistentable(Object object) {
		EntityMapInfo emi = null;
		Class cls = object.getClass();
		if (!clsMaps.containsKey(cls)) {
			emi = EntityResolver.resolve(cls);
			clsMaps.put(cls, emi);
		} else
			emi = clsMaps.get(cls);
		return emi;
	}

	private EntityMapInfo getEntityMapInfo(Object object) {
		EntityMapInfo emi = null;
		Class cls = object.getClass();
		if (!clsMaps.containsKey(cls)) {
			emi = EntityResolver.resolve(cls);
			clsMaps.put(cls, emi);
		} else
			emi = clsMaps.get(cls);
		return emi;
	}

	@Override
	public <T> List<T> findAll(Class<T> cls) throws Exception {
		checkDirty();
		List<T> list = new ArrayList<T>();
		mReadLock.lock();
		try {

			long t = System.currentTimeMillis();
			EntityMapInfo emi = null;
			if (!clsMaps.containsKey(cls)) {
				emi = EntityResolver.resolve(cls);
				clsMaps.put(cls, emi);
			} else
				emi = clsMaps.get(cls);
			String sql = "select * from " + emi.table;
			sql = appendOrderBy(sql, null, emi);
			Cursor cursor = db.rawQuery(sql, null);
			while (cursor.moveToNext()) {
				T o = cls.newInstance();
				Iterator<Field> i = emi.fieldsMap.keySet().iterator();
				while (i.hasNext()) {
					Field f = i.next();
					FieldMapInfo fi = emi.fieldsMap.get(f);
					setFieldValue(o, f, fi.type, fi.columnName, cursor);
				}
				if (emi.idField != null)
					setFieldValue(o, emi.idField, emi.idFieldInfo.type,
							emi.idFieldInfo.columnName, cursor);
				list.add(o);
			}
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
			Log.d(TAG, String.format("findAll take %dms get %d entities",
					(System.currentTimeMillis() - t), list.size()));
			return list;
		} finally {
			mReadLock.unlock();
		}
	}

	@Override
	public <T> List<T> query(Class intf, String queryMethod, Object... args)
			throws Exception {
		Class parameterTypes[] = new Class[args.length];
		for (int i = 0; i < args.length; i++) {
			parameterTypes[i] = args[i].getClass();
		}
		Method m = intf.getDeclaredMethod(queryMethod, parameterTypes);

		Query q = m.getAnnotation(Query.class);
		if (q == null)
			throw new RuntimeException("Query method " + queryMethod
					+ " doesn't have \"Query\" annotation");

		String sql = q.sql();
		return _query(m, q, sql, args);
	}

	private <T> List<T> _query(Method m, Query q, String sql, Object... args)
			throws InstantiationException, IllegalAccessException {
		Class entityClass = q.entity();
		if (entityClass == null)
			throw new RuntimeException("entity annotation missing");

		int i;
		List<String> params = extractParams(sql);
		Map<String, Object> paramToValue = new HashMap<String, Object>();
		for (i = 0; i < args.length; i++) {
			Object ao = args[i];
			Annotation[][] pas = m.getParameterAnnotations();
			Param p = null;
			for (int k = 0; k < pas[i].length; k++) {
				if (Param.class.equals(pas[i][k].annotationType())) {
					p = (Param) pas[i][k];
					break;
				}
			}
			if (p == null)
				throw new RuntimeException(
						"Parameter doesn't have @Param(name=\"xxxx\") annotation");
			if (!params.contains(p.name()))
				throw new RuntimeException("Parameter " + p.name()
						+ " doesn't exist in SQL");
			paramToValue.put(p.name(), ao);
		}
		return _doquery(sql, entityClass, params, paramToValue, null);
	}

	private <T> List<T> _doquery(String sql, Class entityClass,
			List<String> params, Map<String, Object> paramToValue,
			String orderBy) throws InstantiationException,
			IllegalAccessException {
		int i;
		for (i = 0; i < params.size(); i++) {
			String param = params.get(i);
			Object ao = paramToValue.get(param);
			sql = sql.replaceAll("#\\{" + param + "\\}", ao.toString());
		}

		EntityMapInfo emi;
		if (!clsMaps.containsKey(entityClass)) {
			emi = EntityResolver.resolve(entityClass);
			clsMaps.put(entityClass, emi);
		} else
			emi = clsMaps.get(entityClass);

		sql = appendOrderBy(sql, orderBy, emi);
		Log.d(TAG, "query sql " + sql);
		List<T> list = new ArrayList<T>();
		mReadLock.lock();
		try {

			long t = System.currentTimeMillis();

			Cursor cursor = db.rawQuery(sql, null);
			while (cursor.moveToNext()) {
				T o = (T) entityClass.newInstance();
				Iterator<Field> it = emi.fieldsMap.keySet().iterator();
				while (it.hasNext()) {
					Field f = it.next();
					FieldMapInfo fi = emi.fieldsMap.get(f);
					setFieldValue(o, f, fi.type, fi.columnName, cursor);
				}
				if (emi.idField != null)
					setFieldValue(o, emi.idField, emi.idFieldInfo.type,
							emi.idFieldInfo.columnName, cursor);
				list.add(o);
			}
			if (cursor != null && !cursor.isClosed()) {
				cursor.close();
			}
			Log.d(TAG, String.format(" query takes %dms get %d entities",
					(System.currentTimeMillis() - t), list.size()));
			return list;
		} finally {
			mReadLock.unlock();
		}
	}

	private String appendOrderBy(String sql, String orderBy, EntityMapInfo emi) {
		if (orderBy == null) {
			boolean ordered = false;
			StringBuffer osb = new StringBuffer();
			if (emi.idField != null && emi.idFieldInfo.orderBy) {
				osb.append(" order by " + emi.idFieldInfo.columnName + " ");
				osb
						.append(emi.idFieldInfo.order == Order.DESC ? "DESC"
								: "ASC");
				osb.append(",");
				ordered = true;
			}

			// check other columns for order
			Iterator<Field> it = emi.fieldsMap.keySet().iterator();
			while (it.hasNext()) {
				Field f = it.next();
				FieldMapInfo fmi = emi.fieldsMap.get(f);
				if (fmi.orderBy) {
					if (!ordered) {
						osb.append(" order by ");
						ordered = true;
					}
					osb.append(" ");
					osb.append(fmi.columnName);
					osb.append(" ");
					osb.append(fmi.order == Order.DESC ? "DESC" : "ASC");
					osb.append(",");
				}
			}
			if (osb.length() > 0) {
				osb.deleteCharAt(osb.length() - 1);
				sql += " " + osb.toString();
			}
		} else
			sql += " " + orderBy;
		return sql;
	}

	private List<String> extractParams(String sql) {
		List<String> params = new ArrayList<String>();
		int i = 0;
		while (i > -1) {
			i = sql.indexOf("#{", i);
			if (i > -1) {
				int si = i + 2;
				i = sql.indexOf("}", si);
				if (i > -1) {
					String param = sql.substring(si, i);
					if (!params.contains(param))
						params.add(param);
					i += param.length();
				}
			}
		}

		return params;
	}

	@Override
	public void queryAsync(final Class intf, final String queryMethod,
			final ResultCallback callback, final int call_id,
			final Object... args) throws Exception {
		Log.d(TAG, String.format("queryAsync id=%d, queryMethod=%s", call_id,
				queryMethod));
		Runnable r = new Runnable() {

			@Override
			public void run() {
				try {
					List data = query(intf, queryMethod, callback, call_id,
							args);
					Log.d(TAG, String.format("queryAsync callback id=%d",
							call_id));

					callback.onResult(data, call_id);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		};
		mQueue.execute(r);
	}

	@Override
	public <T> List<T> query(String sql, Class T,
			Map<String, Object> paramValues) throws Exception {
		List<String> params = extractParams(sql);
		return _doquery(sql, T, params, paramValues, null);
	}

	@Override
	public void queryAsync(final String sql, final Class T,
			final ResultCallback callback, final int call_id,
			final Map<String, Object> paramValues) throws Exception {
		Log.d(TAG, String.format("queryAsync id=%d, sql=%s", call_id, sql));
		Runnable r = new Runnable() {

			@Override
			public void run() {
				try {
					List data = query(sql, T, paramValues);
					Log.d(TAG, String.format("queryAsync callback id=%d",
							call_id));

					callback.onResult(data, call_id);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		};
		mQueue.execute(r);
	}

	CallbackQueue mQueue = new CallbackQueue(4);

	public class CallbackQueue {
		private final int nThreads;
		private final PoolWorker[] threads;
		private final LinkedList queue;

		public CallbackQueue(int nThreads) {
			this.nThreads = nThreads;
			queue = new LinkedList();
			threads = new PoolWorker[nThreads];

			for (int i = 0; i < nThreads; i++) {
				threads[i] = new PoolWorker();
				threads[i].start();
			}
		}

		public void execute(Runnable r) {
			synchronized (queue) {
				queue.addLast(r);
				queue.notify();
			}
		}

		private class PoolWorker extends Thread {
			public void run() {
				Runnable r;

				while (true) {
					synchronized (queue) {
						while (queue.isEmpty()) {
							try {
								queue.wait();
							} catch (InterruptedException ignored) {
							}
						}

						r = (Runnable) queue.removeFirst();
					}

					// If we don't catch RuntimeException,
					// the pool could leak threads
					try {
						r.run();
					} catch (RuntimeException e) {
						// You might want to log something here
					}
				}
			}
		}
	}

	public Map<Class, EntityMapInfo> getClsMaps() {
		return clsMaps;
	}

	@Override
	public <T> T fromJson(String json, Class T) throws Exception {
		EntityMapInfo emi = null;
		if (!clsMaps.containsKey(T)) {
			emi = EntityResolver.resolve(T);
			clsMaps.put(T, emi);
		} else
			emi = clsMaps.get(T);
		T o = (T) T.newInstance();
		JsonFactory f = new JsonFactory();
		// [JACKSON-259]: ability to suppress canonicalization
		f.disable(JsonParser.Feature.CANONICALIZE_FIELD_NAMES);
		JsonParser jp = f.createJsonParser(json);

		jp.nextToken(); // will return JsonToken.START_OBJECT (verify?)
		boolean startEntity = false;
		while (jp.nextToken() != JsonToken.END_OBJECT) {
			String fieldname = jp.getCurrentName();
			Log.d(TAG, "fieldname=" + fieldname);
			JsonToken t = jp.nextToken(); // move to value, or
			// START_OBJECT/START_ARRAY
			if (emi.idField != null && emi.idFieldInfo.jsonField != null) {
				if (emi.idFieldInfo.jsonField.equals(fieldname)) {
					// bind id field
					if (emi.idFieldInfo.type == FieldType.SHORT)
						setFieldValue(emi.idField, o, jp.getShortValue());
					else if (emi.idFieldInfo.type == FieldType.INT)
						setFieldValue(emi.idField, o, jp.getIntValue());
					else if (emi.idFieldInfo.type == FieldType.LONG)
						setFieldValue(emi.idField, o, jp.getLongValue());
				}
			} else {
				FieldMapInfo fmi = emi.jsonFieldsMap.get(fieldname);
				if (fmi != null) {
					setValueFromJson(o, jp, fmi);
				}
			}
		}

		jp.close();
		return o;
	}

	private <T> void setValueFromJson(T o, JsonParser jp, FieldMapInfo fmi)
			throws IOException, JsonParseException {
		FieldType type = fmi.type;
		if (type == FieldType.STRING) {
			setFieldValue(fmi.field, o, jp.getText());
		} else if (type == FieldType.BYTE) {
			setFieldValue(fmi.field, o, jp.getByteValue());
		} else if (type == FieldType.CHAR) {
			setFieldValue(fmi.field, o, (char) jp.getIntValue());
		} else if (type == FieldType.BOOLEAN) {
			setFieldValue(fmi.field, o, jp.getBooleanValue());
		} else if (type == FieldType.SHORT) {
			setFieldValue(fmi.field, o, jp.getShortValue());
		} else if (type == FieldType.INT) {
			setFieldValue(fmi.field, o, jp.getIntValue());
		} else if (type == FieldType.LONG) {
			setFieldValue(fmi.field, o, jp.getLongValue());
		} else if (type == FieldType.FLOAT) {
			setFieldValue(fmi.field, o, jp.getFloatValue());
		} else if (type == FieldType.DOUBLE) {
			setFieldValue(fmi.field, o, jp.getDoubleValue());
		} else if (type == FieldType.BLOB) {
			setFieldValue(fmi.field, o, jp.getBinaryValue());
		} else if (type == FieldType.DATE) {
			String sd = jp.getText();
			if (sd != null) {
				java.util.Date date = new java.util.Date(sd);
				setFieldValue(fmi.field, o, date);
			}
		}
	}

	private <T> void setValueFromJson(T o, JsonNode jn, FieldMapInfo fmi)
			throws IOException, JsonParseException {
		FieldType type = fmi.type;
		if (type == FieldType.STRING) {
			setFieldValue(fmi.field, o, jn.getTextValue());
		} else if (type == FieldType.BYTE) {
			setFieldValue(fmi.field, o, (byte)jn.getIntValue());
		} else if (type == FieldType.CHAR) {
			String t = jn.getValueAsText();
			if(t!=null&t.length()>0)
				setFieldValue(fmi.field, o, t.charAt(0) );
			else
				setFieldValue(fmi.field, o, null );
		} else if (type == FieldType.BOOLEAN) {
			setFieldValue(fmi.field, o, jn.getBooleanValue());
		} else if (type == FieldType.SHORT) {
			setFieldValue(fmi.field, o, (short)jn.getIntValue());
		} else if (type == FieldType.INT) {
			setFieldValue(fmi.field, o, jn.getIntValue());
		} else if (type == FieldType.LONG) {
			setFieldValue(fmi.field, o, jn.getLongValue());
		} else if (type == FieldType.FLOAT) {
			setFieldValue(fmi.field, o, jn.getNumberValue().floatValue());
		} else if (type == FieldType.DOUBLE) {
			setFieldValue(fmi.field, o, jn.getNumberValue().doubleValue());
		} else if (type == FieldType.BLOB) {
			setFieldValue(fmi.field, o, jn.getBinaryValue());
		} else if (type == FieldType.DATE) {
			String sd = jn.getTextValue();
			if (sd != null) {
				java.util.Date date = new java.util.Date(sd);
				setFieldValue(fmi.field, o, date);
			}
		}
	}

	public <T> T fromJson(String json, String pathToRoot, Class T)
			throws Exception {
		EntityMapInfo emi = null;
		if (!clsMaps.containsKey(T)) {
			emi = EntityResolver.resolve(T);
			clsMaps.put(T, emi);
		} else
			emi = clsMaps.get(T);
		T o = (T) T.newInstance();
		JsonFactory f = new JsonFactory();
		// [JACKSON-259]: ability to suppress canonicalization
		f.disable(JsonParser.Feature.CANONICALIZE_FIELD_NAMES);
		JsonParser jp = f.createJsonParser(json);

		jp.nextToken(); // will return JsonToken.START_OBJECT (verify?)
		String[] pathsegs = pathToRoot.split("/");
		Stack<String> jsonPath = new Stack<String>();
		jsonPath.push("");
		int currentDeepth = 1;
		boolean startEntity = false;
		while (jp.nextToken() != JsonToken.END_OBJECT) {
			String fieldname = jp.getCurrentName();
			Log.d(TAG, "fieldname=" + fieldname);

			JsonToken t = jp.nextToken(); // move to value, or

			if (currentDeepth >= pathsegs.length) {
				startEntity = true;
				for (int i = 0; i < pathsegs.length; i++) {
					if (!pathsegs[i].equals(jsonPath.get(i))) {
						startEntity = false;
						break;
					}
				}
				if (startEntity && t == JsonToken.END_OBJECT)
					break;
				if (startEntity) {
					if (emi.idField != null
							&& emi.idFieldInfo.jsonField != null) {
						if (emi.idFieldInfo.jsonField.equals(fieldname)) {
							// bind id field
							if (emi.idFieldInfo.type == FieldType.SHORT)
								setFieldValue(emi.idField, o, jp
										.getShortValue());
							else if (emi.idFieldInfo.type == FieldType.INT)
								setFieldValue(emi.idField, o, jp.getIntValue());
							else if (emi.idFieldInfo.type == FieldType.LONG)
								setFieldValue(emi.idField, o, jp.getLongValue());
						}
					} else {
						FieldMapInfo fmi = emi.jsonFieldsMap.get(fieldname);
						if (fmi != null) {
							setValueFromJson(o, jp, fmi);
						}
					}
				}
			}

			// START_OBJECT/START_ARRAY
			if (t == JsonToken.START_OBJECT) {
				currentDeepth++;
				jsonPath.push(fieldname);
			} else if (t == JsonToken.END_OBJECT) {
				currentDeepth--;
				jsonPath.pop();
			}
		}

		jp.close();
		return o;
	}

	@Override
	public <T> T insert(String json, Class T) throws Exception {
		T o = fromJson(json, T);
		insert(o);
		return o;
	}

	@Override
	public <T> T insert(String json, String pathToRoot, Class T)
			throws Exception {
		T o = fromJson(json, pathToRoot, T);
		insert(o);
		return null;
	}

	@Override
	public <T> List<T> insertAll(String json, Class T) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> List<T> insertAll(String json, String pathToRoot, Class T)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> List<T> listFromJson(String json, Class T) throws Exception {
		return listFromJson(json, "/", T);
	}

	@Override
	public <T> List<T> listFromJson(String json, String pathToRoot, Class T)
			throws Exception {
		EntityMapInfo emi = null;
		if (!clsMaps.containsKey(T)) {
			emi = EntityResolver.resolve(T);
			clsMaps.put(T, emi);
		} else
			emi = clsMaps.get(T);
		List<T> list = new ArrayList<T>();
		ObjectMapper m = new ObjectMapper();
		// can either use mapper.readTree(JsonParser), or bind to JsonNode
		JsonNode rootNode = m.readValue(json, JsonNode.class);
		String[] segs = pathToRoot.split("/");
		JsonNode arrayNode = null;
		for(int i = 1; i< segs.length ; i ++){
			JsonNode tmpNode = rootNode.path(segs[i]);
			if(i==segs.length-1){
				arrayNode = tmpNode;
			}else{
				rootNode = tmpNode;
			}
		}
		
		if (!arrayNode.isArray())
			return null;
		for (JsonNode n : arrayNode) {
			T o = (T) T.newInstance();
			list.add(o);
			if (emi.idField != null && emi.idFieldInfo.jsonField != null) {
				if (n.has(emi.idFieldInfo.jsonField)) {
					// bind id field
					if (emi.idFieldInfo.type == FieldType.SHORT)
						setFieldValue(emi.idField, o, (short) n.get(
								emi.idFieldInfo.jsonField).getIntValue());
					else if (emi.idFieldInfo.type == FieldType.INT)
						setFieldValue(emi.idField, o, n.get(
								emi.idFieldInfo.jsonField).getIntValue());
					else if (emi.idFieldInfo.type == FieldType.LONG)
						setFieldValue(emi.idField, o, n.get(
								emi.idFieldInfo.jsonField).getLongValue());
				}
			} else {
				Iterator<String> it = emi.jsonFieldsMap.keySet().iterator();
				while (it.hasNext()) {
					String jsonName = it.next();
					FieldMapInfo fmi = emi.jsonFieldsMap.get(jsonName);
					JsonNode jn = n.get(jsonName);
					setValueFromJson(o, jn, fmi);
				}
			}
		}
		return list;
	}

	@Override
	public <T> List<T> query(String sql, String orderBy, Class T,
			Map<String, Object> paramValues) throws Exception {
		List<String> params = extractParams(sql);
		return _doquery(sql, T, params, paramValues, orderBy);

	}

	@Override
	public void queryAsync(final String sql, final String orderBy,
			final Class T, final ResultCallback callback,
			final int callback_id, final Map<String, Object> paramValues)
			throws Exception {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				try {
					List data = query(sql, orderBy, T, paramValues);

					callback.onResult(data, callback_id);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		};
		mQueue.execute(r);
	}

}
