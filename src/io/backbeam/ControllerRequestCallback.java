package io.backbeam;

public abstract class ControllerRequestCallback extends Callback {
	
	public abstract void success(String auth, String user, byte[] body, boolean fromCache);

}
