package io.backbeam;

public abstract class RequestDataCallback extends Callback {

	public abstract void success(byte[] response, boolean fromCache);

}
