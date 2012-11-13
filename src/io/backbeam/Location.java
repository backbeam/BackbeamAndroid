package io.backbeam;

import java.io.Serializable;

public class Location implements Serializable {
	
	private static final long serialVersionUID = -1857479454606070940L;
	
	private double lat;
	private double lon;
	private double alt;
	private String address;
	
	public Location() {
		
	}
	
	public Location(double lat, double lon, double alt, String address) {
		this.lat = lat;
		this.lon = lon;
		this.alt = alt;
		this.address = address;
	}
	
	public Location(double lat, double lon, String address) {
		this.lat = lat;
		this.lon = lon;
		this.address = address;
	}
	
	public Location(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}
	
	public Location(String address) {
		this.address = address;
	}

	public double getLatitude() {
		return lat;
	}

	public void setLatitude(double lat) {
		this.lat = lat;
	}

	public double getLongitude() {
		return lon;
	}

	public void setLongitude(double lon) {
		this.lon = lon;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public double getAltitude() {
		return alt;
	}

	public void setAltitude(double alt) {
		this.alt = alt;
	}

}
