package org.yelsky.fastorm;

import java.util.List;
import java.util.Map;

/**
 * 
 * @author yelsky
 * @email yelsky@gmail.com
 * This is the key interface for fastORM. Any operation needs a session to carry out.
 * 
 */
public interface Session {

	/**
	 * Close session and do clean up.
	 * @throws Exception
	 */
	void close() throws Exception;

	/**
	 * Find a class entity by its' primary key. In SQLite it commonly is an Integer.
	 * @param <T>, return type
	 * @param cls, the entity class
	 * @param primaryKey, primary key of entity such as an Integer or a Long
	 * @return The first entity found
	 * @throws Exception
	 */
	<T> T find(Class<T> cls, Object primaryKey) throws Exception;

	/**
	 * Find all entities belong to this class.
	 * @param <T>, return type
	 * @param cls, the entity class
	 * @return all entities 
	 * @throws Exception
	 */
	<T> List<T> findAll(Class<T> cls) throws Exception;
	
	public <T> List<T> query(String sql,Class T, Map<String, Object> paramValues)
	throws Exception;
	public <T> List<T> query(String sql, String orderBy ,Class T, Map<String, Object> paramValues)
	throws Exception;
	public <T> List<T> query(Class intf, String queryMethod, Object... args)
			throws Exception;

	
	public void queryAsync(String sql, Class T,
			ResultCallback callback,int call_id, Map<String, Object> paramValues) throws Exception;
	public void queryAsync(String sql, String orderBy, Class T,
			ResultCallback callback, int call_id,Map<String, Object> paramValues) throws Exception;

	public void queryAsync(Class intf, String queryMethod,
			ResultCallback callback, int call_id,Object... args) throws Exception;


	
	
	public void insert(Object object) throws Exception;
	
	
	public <T> T insert(String json, Class T) throws Exception;
	
	public <T> List<T> insertAll(String json, Class T) throws Exception;
	
	public <T> T insert(String json, String pathToRoot, Class T) throws Exception;
	
	public <T> List<T> insertAll(String json, String pathToRoot, Class T) throws Exception;
	
	public <T> T fromJson(String json, Class T) throws Exception;
	
	public <T> T fromJson(String json, String pathToRoot,Class T) throws Exception;
	
	public <T> List<T> listFromJson(String json, Class T) throws Exception;
	public <T> List<T> listFromJson(String json, String pathToRoot,Class T) throws Exception;
	
	public void update(Object object) throws Exception;

	public void remove(Object object) throws Exception;

	public void beginTrans() throws Exception;

	public void endTrans() throws Exception;

	public void commit() throws Exception;

	public void evictDirty() throws Exception;

}
