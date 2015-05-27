package org.yelsky.fastorm.impl;

import java.lang.reflect.Field;
import java.util.Hashtable;
import java.util.Map;

/**
 * Entity mapping information.
 * @author yelsky
 * @email yelsky@gmail.com
 *
 */
public class EntityMapInfo {
	/**
	 * Mapped database table
	 */
	public String table;
	/**
	 * Whether to generate table if table doesn't exist in database
	 */
	public boolean generateIfNotExist;
	/**
	 * id field
	 */
	public Field idField;
	/**
	 * id field to database column mapping info
	 */
	public FieldMapInfo idFieldInfo;
	/**
	 * Other fields mapping info
	 */
	public Map<Field, FieldMapInfo> fieldsMap = new Hashtable<Field, FieldMapInfo>();

	
	public Map<String, FieldMapInfo> jsonFieldsMap = new Hashtable<String, FieldMapInfo>();

}
