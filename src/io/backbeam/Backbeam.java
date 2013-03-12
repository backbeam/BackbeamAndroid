package io.backbeam;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.jakewharton.DiskLruCache;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class Backbeam {
	
	private static final String USER_FILE = "backbeam_current_user";
	private static final String REGISTRATION_ID_FILE = "backbeam_registration_id";
	
	private Context context;
	private String host = "backbeamapps.com";
	private int port = 80;
	private String env = "pro";
	private String project;
	private String sharedKey;
	private String secretKey;
	private String twitterConsumerKey;
	private String twitterConsumerSecret;
	private IntentCallback pushHandler;
	private String registrationId;
	private DiskLruCache diskCache;
	
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
	
	public static void close() {
		try {
			instance().diskCache.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	
	public static void setContext(Context context) {
		instance().context = context;
		if (context != null) {
			try {
				File cacheDir = context.getCacheDir();
				instance().diskCache = DiskLruCache.open(cacheDir, 1, 1, 1024*1024);
			} catch (IOException ex) {
				// TODO: handle error
				ex.printStackTrace();
			}
			
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
		instance().perform("POST", "/user/email/login", params, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json json, boolean fromCache) {
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
		instance().perform("POST", "/user/email/lostpassword", params, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json json, boolean fromCache) {
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
	
	protected void perform(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final RequestCallback callback) {
		String url = "http://api."+env+"."+project+"."+host+":"+port+path;
		
		if (params == null) params = new TreeMap<String, Object>();
		params.put("key", this.sharedKey);
		params.put("time", new Date().getTime()+"");
		params.put("nonce", UUID.randomUUID().toString());
		
		RequestParams reqParams = new RequestParams();
		StringBuilder parameterString = new StringBuilder();
		StringBuilder cacheKeyString = new StringBuilder();
		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				Collections.sort(list);
				for (String string : list) {
					parameterString.append("&"+key+"="+string);
					cacheKeyString.append("&"+key+"="+string);
				}
				reqParams.put(key, new ArrayList<String>(list));
			} else {
				// if (value == null) System.out.println("value null "+key);
				parameterString.append("&"+key+"="+value);
				if (!key.equals("time") && !key.equals("nonce")) {
					cacheKeyString.append("&"+key+"="+value);
	            }
				reqParams.put(key, value.toString());
			}
		}
		
		String cacheKey = null;
		final boolean useCache = policy == FetchPolicy.LOCAL_ONLY
						|| policy == FetchPolicy.LOCAL_AND_REMOTE
						|| policy == FetchPolicy.LOCAL_OR_REMOTE;
		
		if (useCache) {
			try {
				cacheKey = Utils.hexString(Utils.sha1(cacheKeyString.toString().getBytes("UTF-8")));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e); // should never happen
			}
			String json = null;
			if (diskCache != null) {
				try {
					json = diskCache.get(cacheKey).getString(0);
				} catch (Exception ex) {
					// ignore
					// ex.printStackTrace();
				}
			}
			boolean read = false;
			if (json != null) {
				Json response = Json.loads(json);
				if (response != null) {
					read = true;
					callback.success(response, true);
					if (policy == FetchPolicy.LOCAL_OR_REMOTE)
						return;
					
				}
			}
			if (policy == FetchPolicy.LOCAL_ONLY) {
				if (!read) {
					callback.failure(new BackbeamException("CachedDataNotFound"));
				}
				return;
			}
		}
		
		final String _cacheKey = cacheKey;
		reqParams.put("signature", Utils.hmacSha1(this.secretKey, parameterString.substring(1)));
		
		// System.out.println("url = "+url);
		AsyncHttpClient client = new AsyncHttpClient();
		AsyncHttpResponseHandler handler = new AsyncHttpResponseHandler() {
		    @Override
		    public void onSuccess(String json) {
		        Json response = Json.loads(json);
		        callback.success(response, false);
		        
		        if (useCache) {
		            try {
						DiskLruCache.Editor editor = diskCache.edit(_cacheKey);
						editor.set(0, json);
						editor.commit();
						diskCache.flush();
					} catch (IOException e) {
						e.printStackTrace(); // TODO: handle error
					}
		        }
		    }
		    
		    @Override
		    public void onFailure(Throwable throwable, String arg1) {
		    	callback.failure(new BackbeamException(throwable));
		    }
		    
		    @Override
		    public void onFinish() {
		    }
		};
		
		if (method.equals("GET")) {
			client.get(url, reqParams, handler);
		} else if (method.equals("POST")) {
			client.addHeader("Content-Type", "application/x-www-form-urlencoded");
			client.post(url, reqParams, handler);
		} else if (method.equals("PUT")) {
			client.addHeader("Content-Type", "application/x-www-form-urlencoded");
			client.put(url, reqParams, handler);
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
		instance().perform("POST", path, params, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json json, boolean fromCache) {
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
		
		instance().perform("POST", "/push/send", params, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json json, boolean fromCache) {
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
