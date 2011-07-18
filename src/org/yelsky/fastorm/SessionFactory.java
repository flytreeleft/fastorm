package org.yelsky.fastorm;

import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.yelsky.fastorm.impl.EntityMapInfo;
import org.yelsky.fastorm.impl.EntityResolver;
import org.yelsky.fastorm.impl.FieldMapInfo;
import org.yelsky.fastorm.impl.FieldType;
import org.yelsky.fastorm.impl.SessionImp;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * 
 * @author yelsky
 * @email yelsky@gmail.com
 * Session factory.
 */
public class SessionFactory {
	private static final String TAG = "SessionFactory";
	static Map<SQLiteDatabase, SessionFactory> factoryMap = new Hashtable<SQLiteDatabase, SessionFactory>();

	public static synchronized SessionFactory getFactory(SQLiteDatabase database) {
		if (factoryMap.containsKey(database))
			return factoryMap.get(database);
		SessionFactory sf = new SessionFactory(database);
		factoryMap.put(database, sf);
		return sf;
	}

	private SQLiteDatabase db;
	private SessionImp s;
	private SessionFactory(SQLiteDatabase database) {
		this.db = database;
	}
	public Session getSession()
	{
		if(s!=null) return s;
		s = new SessionImp(db);
		return s;
	}
	
	public Session getSession(List<Class> clsses){
		SessionImp s = (SessionImp)getSession();
		for ( Class cls : clsses ){
			 EntityMapInfo emi = EntityResolver.resolve(cls);
			 s.getClsMaps().put(cls, emi);
			 if(emi.generateIfNotExist){
				 String sql = genTable(emi);
				 Log.d(TAG,"SQL="+sql);
				 db.execSQL(sql);
			 }
		}
		
		return s;
	}
	private String genTable(EntityMapInfo emi) {
		StringBuffer sb = new StringBuffer();
		sb.append("CREATE TABLE IF NOT EXISTS " );
		sb.append(emi.table);
		sb.append("(");
		if(emi.idField!=null){
			sb.append(emi.idFieldInfo.columnName);
			sb.append(" ");
			if (emi.idFieldInfo.type == FieldType.SHORT) {
				sb.append(" short primary key autoincrement ,");
			} else if (emi.idFieldInfo.type == FieldType.INT) {
				sb.append(" integer primary key autoincrement ,");
			} else if (emi.idFieldInfo.type == FieldType.LONG) {
				sb.append(" long primary key autoincrement ,");
			}
		}
		Iterator<Field> itr = emi.fieldsMap.keySet().iterator();
		while (itr.hasNext()) {
			Field f = itr.next();
			FieldMapInfo fmi = emi.fieldsMap.get(f);
			sb.append(fmi.columnName);
			sb.append(" ");
			if (fmi.type == FieldType.STRING) {
				sb.append(" text");
			} else if (fmi.type == FieldType.BYTE) {
				sb.append(" char");
			} else if (fmi.type == FieldType.BOOLEAN) {
				sb.append(" int1");
			} else if (fmi.type == FieldType.CHAR) {
				sb.append(" char");
			} else if (fmi.type == FieldType.SHORT) {
				sb.append(" short");
			}else if (fmi.type == FieldType.INT) {
				sb.append(" int");
			} else if (fmi.type == FieldType.LONG) {
				sb.append(" int8");
			} else if (fmi.type == FieldType.FLOAT) {
				sb.append(" float");
			} else if (fmi.type == FieldType.DOUBLE) {
				sb.append(" double");
			}else if (fmi.type == FieldType.BLOB) {
				sb.append(" longblob");
			}else if (fmi.type == FieldType.DATE) {
				sb.append(" datetime");
			}
			if(fmi.nullable)
				sb.append(" default null");
			sb.append(",");
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(")");
		return sb.toString();
	}
}
