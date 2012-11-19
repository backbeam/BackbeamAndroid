package io.backbeam;

import android.content.Intent;

public abstract class IntentCallback extends Callback {
	
	public abstract void handleMessage(Intent intent);

}
