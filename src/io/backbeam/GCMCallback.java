package io.backbeam;

public abstract class GCMCallback {
	
	public abstract void deviceRegistered(String registrationId);
	
	public abstract void deviceUnregistered(String registrationId);

	public abstract void serviceNotAvailable();
	
	public abstract void unrecoverableError(String error);

}
