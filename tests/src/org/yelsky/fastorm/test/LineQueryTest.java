package org.yelsky.fastorm.test;

import java.util.List;

import org.yelsky.fastorm.annotation.Param;
import org.yelsky.fastorm.annotation.Query;


public interface LineQueryTest {
	@Query(entity = Line.class, sql = "select * from lines where id>#{id} and name like '%#{name}%'")
	List<Line> queryById(@Param(name="id") Integer id, @Param(name="name") String name);
}
