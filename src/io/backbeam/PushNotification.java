package io.backbeam;

public class PushNotification {
	
	private String text;
	private String sound;
	private int badge;
	private Object extra;
	
	public String getText() {
		return text;
	}
	
	public void setText(String text) {
		this.text = text;
	}
	
	public String getSound() {
		return sound;
	}
	
	public void setSound(String sound) {
		this.sound = sound;
	}
	
	public int getBadge() {
		return badge;
	}
	
	public void setBadge(int badge) {
		this.badge = badge;
	}
	
	public Object getExtra() {
		return extra;
	}
	
	public void setExtra(Object extra) {
		this.extra = extra;
	}

}
