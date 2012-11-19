package io.backbeam;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class Backbeam {
	
	private static final String USER_FILE = "backbeam_current_user";
	private static final String REGISTRATION_ID_FILE = "backbeam_registration_id";
	
	private Context context;
	private String host = "backbeam.io";
	private int port = 80;
	private String env  = "pro";
	private String project;
	private String twitterConsumerKey;
	private String twitterConsumerSecret;
	private IntentCallback pushHandler;
	private String registrationId;
	
	private BackbeamObject currentUser;
	
	private static Backbeam instance;
	
	private Backbeam() {
		
	}
	
	protected static Backbeam instance() {
		if (instance == null) {
			instance = new Backbeam();
		}
		return instance;
	}
	
	public static void setHost(String host) {
		instance().host = host;
	}
	
	public static void setPort(int port) {
		instance().port = port;
	}
	
	public static void setProject(String project) {
		instance().project = project;
	}
	
	public static void setEnvironment(String environment) {
		instance().env = environment;
	}
	
	public static Query select(String entity) {
		return new Query(entity);
	}
	
	public static void logout() {
		setCurrentUser(null);
	}
	
	public static BackbeamObject currentUser() {
		return instance().currentUser;
	}

	protected static void setCurrentUser(BackbeamObject obj) {
		instance().currentUser = obj;
		if (obj != null) {
			try {
				FileOutputStream fos = instance().context.openFileOutput(USER_FILE, Context.MODE_PRIVATE);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(obj);
				fos.close();
			} catch(Exception e) {
				throw new BackbeamException(e);
			}
		} else {
			instance().context.deleteFile(USER_FILE);
		}
	}
	
	public static void setContext(Context context) {
		instance().context = context;
		if (context != null) {
			if (instance().currentUser == null) {
				try {
					FileInputStream fis = instance().context.openFileInput(USER_FILE);
					ObjectInputStream ois = new ObjectInputStream(fis);
					BackbeamObject object = (BackbeamObject) ois.readObject();
					instance().currentUser = object;
					fis.close();
				} catch(FileNotFoundException e) {
					// no user stored
				} catch(Exception e) {
					// ignore? log?
					// throw new BackbeamException(e);
				}
			}
			
			if (instance().registrationId == null) {
				try {
					FileInputStream fis = instance().context.openFileInput(REGISTRATION_ID_FILE);
					DataInputStream dis = new DataInputStream(fis);
					instance().registrationId = dis.readUTF();
					fis.close();
				} catch(FileNotFoundException e) {
					// no registration id stored
				} catch(Exception e) {
					// ignore? log?
					// throw new BackbeamException(e);
				}
			}
		}
	}
	
	public static void login(String email, String password, final ObjectCallback callback) {
		RequestParams params = new RequestParams();
		params.add("email", email);
		params.add("password", password);
		instance().perform("POST", "/user/email/login", params, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				if (status == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				BackbeamObject object = null;
				Json obj = json.get("object");
				if (obj != null) {
					object = new BackbeamObject("user", obj, null, null);
					setCurrentUser(object);
				}
				callback.success(object);
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public static void requestPasswordReset(String email, final OperationCallback callback) {
		RequestParams params = new RequestParams();
		params.add("email", email);
		instance().perform("POST", "/user/email/lostpassword", params, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				if (status == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				if (!status.equals("Success")) {
					callback.failure(new BackbeamException(status));
					return;
				}
				callback.success();
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	protected void perform(String method, String path, RequestParams params, final RequestCallback callback) {
		String url = "http://api."+env+"."+project+"."+host+":"+port+path;
		// System.out.println("url = "+url);
		AsyncHttpClient client = new AsyncHttpClient();
		AsyncHttpResponseHandler handler = new AsyncHttpResponseHandler() {
		    @Override
		    public void onSuccess(String json) {
		        Json response = Json.loads(json);
		        callback.success(response);
		    }
		    
		    @Override
		    public void onFailure(Throwable throwable, String arg1) {
		    	callback.failure(new BackbeamException(throwable));
		    }
		    
		    @Override
		    public void onFinish() {
		    }
		};
		
		if (params == null) params = new RequestParams();
		if (method.equals("GET")) {
			client.get(url, params, handler);
		} else if (method.equals("POST")) {
			client.addHeader("Content-Type", "application/x-www-form-urlencoded");
			client.post(url, params, handler);
		} else if (method.equals("PUT")) {
			client.addHeader("Content-Type", "application/x-www-form-urlencoded");
			client.put(url, params, handler);
		} else if (method.equals("DELETE")) {
			// TODO
			client.addHeader("Content-Type", "application/x-www-form-urlencoded");
		} else {
			callback.failure(new BackbeamException("Unknown HTTP method: "+method));
		}
	}
	
	public static void enableGCM(String senderID) {
    	Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
    	// sets the app name in the intent
    	Context context = instance().context;
    	registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
    	registrationIntent.putExtra("sender", senderID);
    	context.startService(registrationIntent);
	}
	
	public static void setPushNotificationHandler(IntentCallback callback) {
		instance().pushHandler = callback;
	}
	
	static void handleMessage(Intent intent) {
		IntentCallback callback = instance().pushHandler;
		if (callback != null) {
			callback.handleMessage(intent);
		}
	}
	
	static void storeRegistrationId(String registrationID) {
		instance().registrationId = registrationID;
		FileOutputStream fos = null;
		try {
			fos = instance().context.openFileOutput(REGISTRATION_ID_FILE, Context.MODE_PRIVATE);
			DataOutputStream dos = new DataOutputStream(fos);
			dos.writeUTF(registrationID);
			dos.close();
		} catch (Exception e) {
			// TODO
		} finally {
			try {
				if (fos != null) fos.close();
			} catch (IOException e) {
			}
		}
	}
	
	private static boolean subscriptionToChannels(String path, final OperationCallback callback, String... channels) {
		String registrationId = instance().registrationId;
		if (registrationId == null) return false;
		
		RequestParams params = new RequestParams();
		params.add("gateway", "gcm");
		params.add("token", registrationId);
		for (String channel : channels) {
			params.add("channels", channel);
		}
		instance().perform("POST", path, params, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				if (status == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				if (!status.equals("Success")) {
					callback.failure(new BackbeamException(status));
					return;
				}
				callback.success();
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
		return true;
	}
	
	public static boolean subscribeToChannels(final OperationCallback callback, String... channels) {
		return subscriptionToChannels("/push/subscribe", callback, channels);
	}
	
	public static boolean unsubscribeFromChannels(final OperationCallback callback, String... channels) {
		return subscriptionToChannels("/push/unsubscribe", callback, channels);
	}
	
	public static void sendPushNotificationToChannel(PushNotification notification, String channel, final OperationCallback callback) {
		RequestParams params = new RequestParams();
		params.add("channel", channel);
		
		// for iOS
		if (notification.getIosBadge() != null) {
			params.add("apn_badge", notification.getIosBadge().toString());
		}
		if (notification.getIosAlert() != null) {
			params.add("apn_alert", notification.getIosAlert());
		}
		if (notification.getIosSound() != null) {
			params.add("apn_sound", notification.getIosSound());
		}
		if (notification.getIosPayload() != null) {
			for (Map.Entry<String, String> entry : notification.getIosPayload().entrySet()) {
				params.add("apn_payload_"+entry.getKey(), entry.getValue());
			}
		}
		// for Android
		if (notification.getAndroidDelayWhileIdle() != null) {
			params.add("gcm_delay_while_idle", notification.getAndroidDelayWhileIdle().toString());
		}
		if (notification.getAndroidCollapseKey() != null) {
			params.add("gcm_collapse_key", notification.getAndroidCollapseKey());
		}
		if (notification.getAndroidTimeToLive() != null) {
			params.add("gcm_time_to_live", notification.getAndroidTimeToLive().toString());
		}
		if (notification.getAndroidData() != null) {
			for (Map.Entry<String, String> entry : notification.getAndroidData().entrySet()) {
				params.add("gcm_data_"+entry.getKey(), entry.getValue());
			}
		}
		
		instance().perform("POST", "/push/send", params, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				if (status == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				if (!status.equals("Success")) {
					callback.failure(new BackbeamException(status));
					return;
				}
				callback.success();
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
}
