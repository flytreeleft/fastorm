# Basic introduction& Background
This is a light weight and easy to use Java Object Relation Map framework.
Especially optimized for speed and multi-thread environments.

The initialization goal of design is that reduce conflict of Android SQLite
based content provider under multi-thread environment.
Due to the lock mechanism of SQLite, the concurrency performance of SQLite
is very poor. for example that if do 5000 insert and 100 query in 3 threads,
sometimes the total time will up to 30 minutes.
The main cause of poor performance is that SQLite takes database level lock
during writing. Any ongoing changes will lock whole database and block any
other operation to go. So the original SQLite is not designed to multi-thread
intensive usage environment. If so the performance will be dramatically
drag down.

This framework resolves the performance issue of SQLite by creating
an in-memory cache of object and applying smart database writing in back ground.

# The main features:
* Dramatically improve record insert performance, sometimes up to 1000x.
The actual benefit depends on the actual insert records number and operation
threads number. More threads and more records get more gain in performance.
* Thread safe
* Java POJO object to database table mapping and verse vice
* Simple annotation to mark out class for mapping
* Auto generate database table if not exist by ```@GenerateIfNotExist``` annotation
* Replace Android default Json parser to Jackson parser. The parsing performance
gain is about 270% up.
* Directly parsing object from Json
* in-memory synchronized table sequence management
* Lazy and smart background database flush

# Comparing to content provider it has following advantages:
* In memory cache to improve writing performance by gathering sparse writing to
a batch writing
* No IPC needed to wrap and unwrap result data set during transferring data between
two processes by binder
* Support intensive multi-thread race condition database access without dramatically
dragging down performance

# Get Start
Step 1, declare entity as below
```
import java.util.Date;

import org.yelsky.fastorm.annotation.Column;
import org.yelsky.fastorm.annotation.Id;
import org.yelsky.fastorm.annotation.JsonField;
import org.yelsky.fastorm.annotation.Order;
import org.yelsky.fastorm.annotation.Table;


@Table(name = "lines")
public  class Line {
        @Id(name = "id")
        public int id;
        @Column(name = "name")
        @JsonField(name="name")
        public String name;
        @Column(name = "number")
        @JsonField(name="number")
        @Order(value=Order.DESC)
        public int number;
        @Column(name = "updatedate")
        @JsonField(name="updatedate")
        @Order(value=Order.ASC)
        public Date updatedate;
}
```
Notes: ```@Table``` annotates the database table, ```@Id``` should be placed on
primary key field , ```@Column``` placed on other table columns

Step 2: prepare the Session like :
```
   Session s = DbUtil.getSession();
```
Step 3: Do real table manipulation like insert, find ,query, remove etc

i. Insert
```
    Line l = new Line();
    l.name = "test line";
    l.number = 10;
    l.updatedate = new java.util.Date();
    s.insert(l);
```
ii. find
```
    Line l = s.find(Line.class,  3/*id*/);
```
iii. query
```
    Map<String, Object> params = new  Map<String, Object>();
    params.put("id", 5);
    params.put("name", "cosmic");
    List<Line> lines = s.query("select * from lines"
                                + " where id>#{id} and name like '%#{name}%'",
                                    Line.class, params);
```
Notes: the parameter in sql must be format ```#{parameter_name}``` ,
and must put a value with the same key to params map.
The sql is any form of native SQLite SQL with unlimited parameters.

iv. remove
```
   s.remove(vg);
```
v. with Json

I.single object from json
```
   //a single object from json
   lineJson = "{ \"line\" : { \"name\": \"Line 1\", \"number\": 1"
              + ", \"updatedate\": \"Tue Nov 04 20:14:11 EST 2003\" } }";
   l = s.fromJson(lineJson, "/line", Line.class);
```
II.list of objects from json
```
   String lineJsonArray = "{\"root\":{"
                               + "\"lines\":[{"
                                   + "\"name\": \"Line 1\", \"number\": 1"
                                   + ", \"updatedate\": \"Tue Nov 04 20:14:11 EST 2003\""
                               + "}]"
                         + "}}";
   List<Line> ls = s.listFromJson(lineJsonArray,"/root/lines", Line.class);
```
vi. flash data to database
```
  s.commit();
```
Notes: if don't call this method, all entities are only in memory
and not flash to database. This method also will be called automatically
if any other issues a read operations such as find/query to ensure the data integrity .

vii. auto generate database table if not exist by ```@GenerateIfNotExist```
```
@Table(name = "lines")
@GenerateIfNotExist
public  class Line {
        @Id(name = "id")
        public int id;
        @Column(name = "name")
        @JsonField(name="name")
        public String name;
        @Column(name = "number")
        @JsonField(name="number")
        @Order(value=Order.DESC)
        public int number;
        @Column(name = "updatedate")
        @JsonField(name="updatedate")
        @Order(value=Order.ASC)
        public Date updatedate;
}
```
viii. Default order field by ```@Order```. If a query doesn't provide an order,
the query/findAll interface will use ```@Order``` annotation to apply a default
order when doing query.