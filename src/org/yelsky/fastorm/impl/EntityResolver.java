package org.yelsky.fastorm.impl;

import java.lang.reflect.Field;

import org.yelsky.fastorm.annotation.Column;
import org.yelsky.fastorm.annotation.EscapeSQL;
import org.yelsky.fastorm.annotation.GenerateIfNotExist;
import org.yelsky.fastorm.annotation.Id;
import org.yelsky.fastorm.annotation.JsonField;
import org.yelsky.fastorm.annotation.Nullable;
import org.yelsky.fastorm.annotation.Order;
import org.yelsky.fastorm.annotation.Table;

import android.util.Log;

/**
 * 
 * @author yelsky
 * @email yelsky@gmail.com
 * This class resolves the entity class's mapping information.
 */
public class EntityResolver {
	private static final String TAG = "EntityResolver";

	/**
	 *  * It scans through all entity classes' persistence required annotations to 
	 * get mapping info about field and field type.
	 * @param cls
	 * @return
	 */
	public static synchronized EntityMapInfo resolve(Class cls) {
		if (!cls.isAnnotationPresent(Table.class))
			throw new RuntimeException("Class " + cls.getName()
					+ " is not persistence class");
		EntityMapInfo emi = new EntityMapInfo();
		Log.d(TAG, "resolving class " + cls.getName());
		emi.table = ((Table) cls.getAnnotation(Table.class)).name();
		emi.generateIfNotExist = cls.isAnnotationPresent(GenerateIfNotExist.class);
			
		for (Field field : cls.getDeclaredFields()) {
			if (field.isAnnotationPresent(Column.class)) {
				Class fcls = field.getType();
				FieldMapInfo fi = new FieldMapInfo();
				fi.columnName = ((Column) field.getAnnotation(Column.class))
						.name();
				fi.field = field;
				fi.nullable = field.isAnnotationPresent(Nullable.class);
				checkFieldType(fcls, fi);
				emi.fieldsMap.put(field, fi);
				//for json
				if(field.isAnnotationPresent(JsonField.class)){
					String jsoname = ((JsonField)field.getAnnotation(JsonField.class)).name();
					fi.jsonField = jsoname;
					emi.jsonFieldsMap.put(jsoname, fi);
				}
				if(fi.type==FieldType.STRING){
					//check for escape char
					if(field.isAnnotationPresent(EscapeSQL.class))
						fi.escapeSQL = true;
				}
				
				if(field.isAnnotationPresent(Order.class)){
					fi.orderBy = true;
					fi.order = ((Order) field.getAnnotation(Order.class)).value();
				}
				Log.d(TAG, String.format("field map %s-->%s ", field.getName(),
						fi.columnName));
			} else if (field.isAnnotationPresent(Id.class)) {
				emi.idField = field;
				emi.idFieldInfo = new FieldMapInfo();
				emi.idFieldInfo.field  = field;
				emi.idFieldInfo.columnName = ((Id) field
						.getAnnotation(Id.class)).name();
				checkFieldType(field.getType(), emi.idFieldInfo);
				Log.d(TAG, String.format("id map %s-->%s ", field.getName(),
						emi.idFieldInfo.columnName));
				//for json
				if(field.isAnnotationPresent(JsonField.class))
					emi.idFieldInfo.jsonField = ((JsonField)field.getAnnotation(JsonField.class)).name();
				if(field.isAnnotationPresent(Order.class)){
					emi.idFieldInfo.orderBy = true;
					emi.idFieldInfo.order = ((Order) field.getAnnotation(Order.class)).value();
				}
			}
		}

		return emi;
	}
	/**
	 * check field type
	 * @param fcls
	 * @param fi
	 */
	private static void checkFieldType(Class fcls, FieldMapInfo fi) {
		if (String.class.equals(fcls)) {
			fi.type = FieldType.STRING;
		} else if (byte.class.equals(fcls) || Byte.class.equals(fcls)) {
			fi.type = FieldType.BYTE;
		} else if (boolean.class.equals(fcls) || Boolean.class.equals(fcls)) {
			fi.type = FieldType.BOOLEAN;
		} else if (char.class.equals(fcls) || Character.class.equals(fcls)) {
			fi.type = FieldType.CHAR;
		} else if (short.class.equals(fcls) || Short.class.equals(fcls)) {
			fi.type = FieldType.SHORT;
		}else if (int.class.equals(fcls) || Integer.class.equals(fcls)) {
			fi.type = FieldType.INT;
		} else if (long.class.equals(fcls) || Long.class.equals(fcls)) {
			fi.type = FieldType.LONG;
		} else if (float.class.equals(fcls) || Float.class.equals(fcls)) {
			fi.type = FieldType.FLOAT;
		} else if (double.class.equals(fcls) || Double.class.equals(fcls)) {
			fi.type = FieldType.DOUBLE;
		}else if (byte[].class.equals(fcls) || Byte[].class.equals(fcls)) {
			fi.type = FieldType.BLOB;
		}else if (java.util.Date.class.equals(fcls)) {
			fi.type = FieldType.DATE;
		}
	}
}
