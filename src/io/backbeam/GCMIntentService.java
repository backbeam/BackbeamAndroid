package io.backbeam;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class GCMIntentService extends IntentService {

	private static PowerManager.WakeLock sWakeLock;
    private static final Object LOCK = GCMIntentService.class;
    
    public GCMIntentService(String name) {
		super(name);
	}
    
    public GCMIntentService() {
		super("");
	}

    public static void runIntentInService(Context context, Intent intent) {
        synchronized(LOCK) {
            if (sWakeLock == null) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "my_wakelock");
            }
        }
        sWakeLock.acquire();
        intent.setClassName(context, GCMIntentService.class.getName());
        context.startService(intent);
    }
    
    @Override
    public final void onHandleIntent(Intent intent) {
        try {
            String action = intent.getAction();
            // System.out.println("action = "+action);
            if (action.equals("com.google.android.c2dm.intent.REGISTRATION")) {
                handleRegistration(intent);
            } else if (action.equals("com.google.android.c2dm.intent.RECEIVE")) {
            	Backbeam.handleMessage(intent);
            }
        } finally {
            synchronized(LOCK) {
                sWakeLock.release();
            }
        }
    }
    
    private void handleRegistration(Intent intent) {
        String registrationId = intent.getStringExtra("registration_id");
        String error = intent.getStringExtra("error");
        String unregistered = intent.getStringExtra("unregistered");       
        // registration succeeded
        if (registrationId != null) {
            // store registration ID on shared preferences
            // notify 3rd-party server about the registered ID
        	Backbeam.storeRegistrationId(registrationId);
        	if (Backbeam.instance().gcmCallback != null) {
        		Backbeam.instance().gcmCallback.deviceRegistered(registrationId);
        	}
        }
            
        // unregistration succeeded
        if (unregistered != null) {
            // get old registration ID from shared preferences
            // notify 3rd-party server about the unregistered ID
        	// TODO: System.out.println("unregistered");
        	if (Backbeam.instance().gcmCallback != null) {
        		Backbeam.instance().gcmCallback.deviceUnregistered(unregistered);
        	}
        } 
            
        // last operation (registration or unregistration) returned an error;
        if (error != null) {
            if ("SERVICE_NOT_AVAILABLE".equals(error)) {
               // optionally retry using exponential back-off
               // (see Advanced Topics)
            	if (Backbeam.instance().gcmCallback != null) {
            		Backbeam.instance().gcmCallback.serviceNotAvailable();
            	}
            } else {
                // Unrecoverable error, log it
                Log.i("TAG", "Received error: " + error);
            	if (Backbeam.instance().gcmCallback != null) {
            		Backbeam.instance().gcmCallback.unrecoverableError(error);
            	}
            }
        }
    }
    
}
