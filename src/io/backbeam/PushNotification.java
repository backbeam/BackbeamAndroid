package io.backbeam;

import java.util.HashMap;
import java.util.Map;

public class PushNotification {
	
	private String iosAlert;
	private String iosSound;
	private Integer iosBadge;
	private Map<String, String> iosPayload;
	
	private String androidCollapseKey;
	private Boolean androidDelayWhileIdle;
	private Long androidTimeToLive;
	private Map<String, String> androidData;
	
	public String getAndroidCollapseKey() {
		return androidCollapseKey;
	}

	public void setAndroidCollapseKey(String androidCollapseKey) {
		this.androidCollapseKey = androidCollapseKey;
	}

	public Boolean getAndroidDelayWhileIdle() {
		return androidDelayWhileIdle;
	}

	public void setAndroidDelayWhileIdle(Boolean androidDelayWhileIdle) {
		this.androidDelayWhileIdle = androidDelayWhileIdle;
	}

	public Long getAndroidTimeToLive() {
		return androidTimeToLive;
	}

	public void setAndroidTimeToLive(Long timeToLive) {
		this.androidTimeToLive = timeToLive;
	}

	public Map<String, String> getIosPayload() {
		return iosPayload;
	}
	
	public void addIosPayload(String key, String value) {
		if (iosPayload == null) iosPayload = new HashMap<String, String>();
		iosPayload.put(key, value);
	}

	public void setIosPayload(Map<String, String> iosPayload) {
		this.iosPayload = iosPayload;
	}

	public Map<String, String> getAndroidData() {
		return androidData;
	}

	public void setAndroidData(Map<String, String> androidData) {
		this.androidData = androidData;
	}
	
	public void addAndroidData(String key, String value) {
		if (androidData == null) androidData = new HashMap<String, String>();
		androidData.put(key, value);
	}

	public String getIosAlert() {
		return iosAlert;
	}
	
	public void setIosAlert(String text) {
		this.iosAlert = text;
	}
	
	public String getIosSound() {
		return iosSound;
	}
	
	public void setIosSound(String sound) {
		this.iosSound = sound;
	}
	
	public Integer getIosBadge() {
		return iosBadge;
	}
	
	public void setIosBadge(Integer badge) {
		this.iosBadge = badge;
	}

}
