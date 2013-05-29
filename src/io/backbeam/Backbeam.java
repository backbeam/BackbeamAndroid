package io.backbeam;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

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
	private IntentCallback pushHandler;
	private String registrationId;
	private DiskLruCache diskCache;
	
	private BackbeamObject currentUser;
	private SocketIO socketio;
	private Map<String, List<RealTimeEventListener>> roomListeners;
	private List<RealTimeConnectionListener> realTimeListeners;
	
	private static Backbeam instance;
	
	private Backbeam() {
		roomListeners = new HashMap<String, List<RealTimeEventListener>>();
		realTimeListeners = new ArrayList<RealTimeConnectionListener>();
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
	
	private String roomName(String eventName) {
		return project+"/"+env+"/"+eventName;
	}
	
	public static void subscribeToRealTimeEvents(String eventName, RealTimeEventListener listener) {
		instance()._subscribeToRealTimeEvents(eventName, listener);
	}
	
	public void _subscribeToRealTimeEvents(String eventName, RealTimeEventListener listener) {
		String room = roomName(eventName);
		List<RealTimeEventListener> listeners = roomListeners.get(room);
		if (listeners == null) {
			listeners = new ArrayList<RealTimeEventListener>();
			roomListeners.put(room, listeners);
		}
		listeners.add(listener);
		
		if (socketio != null && socketio.isConnected()) {
			TreeMap<String, Object> params = new TreeMap<String, Object>();
			params.put("room", room);
			socketio.emit("subscribe", socketioMessage(params));
		}
	}
	
	private JSONObject socketioMessage(TreeMap<String, Object> params) {
		sign(params, null);
		JSONObject object = new JSONObject(params);
		return object;
	}
	
	public void _unsubscribeFromRealTimeEvents(String eventName, RealTimeEventListener listener) {
		String room = roomName(eventName);
		List<RealTimeEventListener> listeners = roomListeners.get(room);
		if (listeners != null) {
			listeners.remove(listener);
			if (listeners.size() == 0) {
				roomListeners.remove(room);
				if (socketio != null && socketio.isConnected()) {
					TreeMap<String, Object> params = new TreeMap<String, Object>();
					params.put("room", room);
					socketio.emit("unsubscribe", socketioMessage(params));
				}
			}
		}
	}
	
	public static void unsubscribeFromRealTimeEvents(String eventName, RealTimeEventListener listener) {
		instance()._unsubscribeFromRealTimeEvents(eventName, listener);
	}
	
	public void _sendRealTimeEvent(String eventName, Map<String, String> message) {
		if (socketio != null && socketio.isConnected()) {
			String room = roomName(eventName);
			TreeMap<String, Object> params = new TreeMap<String, Object>();
			params.put("room", room);
			for (Map.Entry<String, String> entry : message.entrySet()) {
				params.put("_"+entry.getKey(), entry.getValue());
			}
			socketio.emit("publish", socketioMessage(params));
		}
	}
	
	public static void sendRealTimeEvent(String eventName, Map<String, String> message) {
		instance()._sendRealTimeEvent(eventName, message);
	}
	
	public static void enableRealtime() {
		instance()._enableRealTime();
	}
	
	private void _enableRealTime() {
		if (socketio != null) return;
		reconnect();
	}
	
	public static void disableRealtime() {
		instance()._disableRealTime();
	}
	
	private void _disableRealTime() {
		if (socketio != null) {
			socketio.disconnect();
			socketio = null;
		}
	}
	
	public static void addRealTimeConnectionListener(RealTimeConnectionListener listener) {
		instance().realTimeListeners.add(listener);
	}
	
	public static void removeRealTimeConnectionListener(RealTimeConnectionListener listener) {
		instance().realTimeListeners.remove(listener);
	}
	
	private void fireConnecting() {
		for (RealTimeConnectionListener listener : realTimeListeners) {
			listener.realTimeConnecting();
		}
	}
	
	private void fireConnected() {
		System.out.println("fire connected");
		for (RealTimeConnectionListener listener : realTimeListeners) {
			listener.realTimeConnected();
		}
	}
	
	private void fireDisconnected() {
		for (RealTimeConnectionListener listener : realTimeListeners) {
			listener.realTimeDisconnected();
		}
	}
	
	private void fireFailed(Exception e) {
		for (RealTimeConnectionListener listener : realTimeListeners) {
			listener.realTimeDisconnected();
		}
	}
	
	private void reconnect() {
		// https://github.com/Gottox/socket.io-java-client
		fireConnecting();
		String url = "http://api."+env+"."+project+"."+host+":"+port;
		try {
			socketio = new SocketIO(url, new IOCallback() {
				
				@Override
				public void onMessage(JSONObject json, IOAcknowledge ioa) {
				}
				
				@Override
				public void onMessage(String string, IOAcknowledge ioa) {
				}
				
				@Override
				public void onError(SocketIOException error) {
					fireFailed(error);
					reconnect();
				}
				
				@Override
				public void onDisconnect() {
					fireDisconnected();
				}
				
				@Override
				public void onConnect() {
					for (String room : roomListeners.keySet()) {
						TreeMap<String, Object> params = new TreeMap<String, Object>();
						params.put("room", room);
						socketio.emit("subscribe", socketioMessage(params));
					}
					
					fireConnected();
				}
				
				@Override
				public void on(String event, IOAcknowledge ioa, Object... args) {
					if (args.length > 0 && args[0].getClass() == JSONObject.class) {
						JSONObject message = (JSONObject) args[0];
						try {
							String room = message.getString("room");
							String[] parts = room.split("/");
							if (parts.length == 3 && parts[0].equals(project) && parts[1].equals(env)) {
								String eventName = parts[2];
								List<RealTimeEventListener> listeners = roomListeners.get(room);
								if (listeners.size() > 0) {
									Map<String, String> params = new HashMap<String, String>();
									@SuppressWarnings("unchecked")
									Iterator<String> iter = (Iterator<String>) message.keys();
									while (iter.hasNext()) {
										String key = iter.next();
										if (key.length() > 0 && key.charAt(0) == '_') {
											String value = message.getString(key);
											if (value != null) {
												params.put(key.substring(1), value);
											}
										}
									}
									
									for (RealTimeEventListener listener : listeners) {
										listener.realTimeEventReceived(eventName, params);
									}
								}
								
							}
						} catch(JSONException e) {
							// ignore, wrong message (i.e. not room)
						}
						
					}
				}
			});
		} catch(Exception e) {
			fireFailed(e);
		}
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
	
	public static BackbeamObject read(String entity, String id, String joins, Object[] params, ObjectCallback callback) {
		BackbeamObject obj = new BackbeamObject(entity, id);
		obj.refresh(joins, params, callback);
		return obj;
	}
	
	public static BackbeamObject read(String entity, String id, ObjectCallback callback) {
		BackbeamObject obj = new BackbeamObject(entity, id);
		obj.refresh(callback);
		return obj;
	}
	
	public static void login(String email, String password, final ObjectCallback callback) {
		login(email, password, null, null, callback);
	}
	
	public static void login(String email, String password, String joins, Object[] params, final ObjectCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("email", email);
		prms.put("password", password);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		instance().perform("POST", "/user/email/login", prms, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
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
				BackbeamObject object = loginUserWithResponse(json);
				callback.success(object);
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public static void verifyCode(String code, final ObjectCallback callback) {
		verifyCode(code, null, null, callback);
	}
	
	public static void verifyCode(String code, String joins, Object[] params, final ObjectCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("code", code);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		instance().perform("POST", "/user/email/verify", prms, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
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
				BackbeamObject object = loginUserWithResponse(json);
				callback.success(object);
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	private static BackbeamObject loginUserWithResponse(Json json) {
		String id = json.get("id").asString();
		Json values = json.get("objects");
		Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
		BackbeamObject object = objects.get(id);
		if (object != null) {
			setCurrentUser(object);
		}
		return object;
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
	
	private String sign(TreeMap<String, Object> params, RequestParams reqParams) {
		params.put("key", this.sharedKey);
		params.put("time", new Date().getTime()+"");
		params.put("nonce", UUID.randomUUID().toString());
		
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
				if (reqParams != null) reqParams.put(key, new ArrayList<String>(list));
			} else {
				parameterString.append("&"+key+"="+value);
				if (!key.equals("time") && !key.equals("nonce")) {
					cacheKeyString.append("&"+key+"="+value);
	            }
				if (reqParams != null) reqParams.put(key, value.toString());
			}
		}
		String signature = Utils.hmacSha1(this.secretKey, parameterString.substring(1));
		if (reqParams != null) {
			reqParams.put("signature", signature);
		} else {
			params.put("signature", signature);
		}

		return cacheKeyString.toString();
	}
	
	protected void perform(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final RequestCallback callback) {
		String url = "http://api."+env+"."+project+"."+host+":"+port+path;
		
		if (params == null) params = new TreeMap<String, Object>();
		params.put("key", this.sharedKey);
		params.put("time", new Date().getTime()+"");
		params.put("nonce", UUID.randomUUID().toString());
		params.put("method", method);
		params.put("path", path);
		
		RequestParams reqParams = new RequestParams();
		String cacheKeyString = sign(params, reqParams);
		params.remove("method");
		params.remove("path");
		
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
			reqParams.put("_method", "DELETE");
			client.get(url, reqParams, handler);
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
	
	public static void facebookLogin(String accessToken, String joins, Object[] params, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		socialSignup("facebook", prms, callback);
	}
	
	public static void facebookLogin(String accessToken, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		socialSignup("facebook", prms, callback);
	}
	
	public static void twitterLogin(String oauthToken, String oauthTokenSecret, String joins, Object[] params, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("oauth_token", oauthToken);
		prms.put("oauth_token_secret", oauthTokenSecret);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		socialSignup("twitter", prms, callback);
	}
	
	public static void twitterLogin(String oauthToken, String oauthTokenSecret, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("oauth_token", oauthToken);
		prms.put("oauth_token_secret", oauthTokenSecret);
		socialSignup("twitter", prms, callback);
	}
	
	private static void socialSignup(String provider, TreeMap<String, Object> params, final SignupCallback callback) {
		
		instance().perform("POST", "/user/"+provider+"/signup", params, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json response, boolean fromCache) {
				if (!response.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = response.get("status").asString();
				if (status == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				
				boolean isNew = status.equals("Success");
				if (!isNew && !status.equals("UserAlreadyExists")) {
					callback.failure(new  BackbeamException(status));
					return;
				}
				
		        Json values = response.get("objects");
		        String id   = response.get("id").str();
		        String auth = response.get("auth").str();
		        
		        if (values == null || id == null || auth == null) {
		        	callback.failure(new  BackbeamException(status));
		        	return;
		        }

		        Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
				
				callback.success(objects.get(id), isNew);
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
}
