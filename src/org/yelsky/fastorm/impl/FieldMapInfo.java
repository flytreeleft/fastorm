package org.yelsky.fastorm.impl;

import java.lang.reflect.Field;

public final class FieldMapInfo {
	public String columnName;
	public Field field;
	public boolean nullable;
	public FieldType type;
	public String jsonField;
	public boolean escapeSQL;
	public boolean orderBy;
	public int order;
}
