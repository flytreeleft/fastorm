package org.yelsky.fastorm.test;

/** This is a generated file. 
Author: Sam Tang 
Date: Wed Jul 13 14:15:34 CST 2011*/ 
import org.yelsky.fastorm.annotation.Column;
import org.yelsky.fastorm.annotation.Id;
import org.yelsky.fastorm.annotation.JsonField;
import org.yelsky.fastorm.annotation.Table;
@Table (name="stations")
public class Station {
	@Id(name="_id")
	public int _id;
	@JsonField(name="station_id")
	@Column(name="station_id")
	public String station_id;
	@Column(name="lineup_id")
	public String lineup_id;
	@Column(name="call_sign")
	public String call_sign;
	@JsonField(name="station_name")
	@Column(name="station_name")
	public String station_name;
	@Column(name="channel_number")
	public int channel_number;
	@JsonField(name="affiliate")
	@Column(name="affiliate")
	public String affiliate;
	@Column(name="station_uri")
	public String station_uri;
	@Column(name="logo_uri")
	public String logo_uri;
	@Column(name="logo_id")
	public int logo_id;
	@Column(name="content_location")
	public String content_location;
	@Column(name="created")
	public String created;
	@Column(name="modified")
	public String modified;
}
