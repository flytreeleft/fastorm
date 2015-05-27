package org.yelsky.fastorm.test;

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

