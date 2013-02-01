package io.backbeam;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

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
	private String sharedKey;
	private String secretKey;
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
	
	public static void setSecretKey(String key) {
		instance().secretKey = key;
	}
	
	public static void setSharedKey(String key) {
		instance().sharedKey = key;
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
		TreeMap<String, Object> params = new TreeMap<String, Object>();
		params.put("email", email);
		params.put("password", password);
		instance().perform("POST", "/user/email/login", params, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json values = json.get("objects");
				if (status == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String id = json.get("id").asString();
				Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
				BackbeamObject object = objects.get(id);
				if (object != null) {
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
		TreeMap<String, Object> params = new TreeMap<String, Object>();
		params.put("email", email);
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
	
	private String hmacSha1(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secret = new SecretKeySpec(key.getBytes("UTF-8"), mac.getAlgorithm());
            mac.init(secret);
            byte[] digest = mac.doFinal(message.getBytes("UTF-8"));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new BackbeamException(e);
        }
	}
	
	private RequestParams sign(TreeMap<String, Object> params) {
		if (params == null) params = new TreeMap<String, Object>();
		params.put("key", this.sharedKey);
		params.put("time", new Date().getTime()+"");
		params.put("nonce", UUID.randomUUID().toString());
		
		RequestParams reqParams = new RequestParams();
		StringBuilder base = new StringBuilder();
		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				Collections.sort(list);
				for (String string : list) {
					base.append("&"+key+"="+string);
					reqParams.add(key, string);
				}
			} else {
				if (value == null) System.out.println("value null "+key);
				base.append("&"+key+"="+value);
				reqParams.add(key, value.toString());
			}
		}
		
		reqParams.add("signature", hmacSha1(this.secretKey, base.substring(1)));
		return reqParams;
	}
	
	protected void perform(String method, String path, TreeMap<String, Object> prms, final RequestCallback callback) {
		String url = "http://api."+env+"."+project+"."+host+":"+port+path;
		RequestParams params = sign(prms);
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
		
		TreeMap<String, Object> params = new TreeMap<String, Object>();
		params.put("gateway", "gcm");
		params.put("token", registrationId);
		params.put("channels", Arrays.asList(channels));
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
		TreeMap<String, Object> params = new TreeMap<String, Object>();
		params.put("channel", channel);
		
		// for iOS
		if (notification.getIosBadge() != null) {
			params.put("apn_badge", notification.getIosBadge().toString());
		}
		if (notification.getIosAlert() != null) {
			params.put("apn_alert", notification.getIosAlert());
		}
		if (notification.getIosSound() != null) {
			params.put("apn_sound", notification.getIosSound());
		}
		if (notification.getIosPayload() != null) {
			for (Map.Entry<String, String> entry : notification.getIosPayload().entrySet()) {
				params.put("apn_payload_"+entry.getKey(), entry.getValue());
			}
		}
		// for Android
		if (notification.getAndroidDelayWhileIdle() != null) {
			params.put("gcm_delay_while_idle", notification.getAndroidDelayWhileIdle().toString());
		}
		if (notification.getAndroidCollapseKey() != null) {
			params.put("gcm_collapse_key", notification.getAndroidCollapseKey());
		}
		if (notification.getAndroidTimeToLive() != null) {
			params.put("gcm_time_to_live", notification.getAndroidTimeToLive().toString());
		}
		if (notification.getAndroidData() != null) {
			for (Map.Entry<String, String> entry : notification.getAndroidData().entrySet()) {
				params.put("gcm_data_"+entry.getKey(), entry.getValue());
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
