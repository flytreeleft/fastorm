package org.yelsky.fastorm.test;

import java.util.Date;

import org.yelsky.fastorm.annotation.Column;
import org.yelsky.fastorm.annotation.GenerateIfNotExist;
import org.yelsky.fastorm.annotation.Id;
import org.yelsky.fastorm.annotation.Table;

@GenerateIfNotExist
@Table(name="Team")
public class Team {
	@Id(name="id")
	public int id;
	@Column(name="name")
	public String name;
	@Column(name="flag")
	public byte flag;
	@Column(name="num")
	public short num;
	@Column(name="members")
	public long members;
	@Column(name="createTime")
	public Date createTime;
	@Column(name="image")
	public byte[] image;
	@Column(name="status")
	public char status;
}
