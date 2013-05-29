package io.backbeam;

public interface RealTimeConnectionListener {
	
	public void realTimeConnecting();
	
	public void realTimeConnected();
	
	public void realTimeDisconnected();
	
	public void realTimeConnectionFailed(Exception e);

}
