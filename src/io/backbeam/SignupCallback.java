package io.backbeam;

public abstract class SignupCallback extends Callback {
	
	public abstract void success(BackbeamObject user, boolean isNew);

}
