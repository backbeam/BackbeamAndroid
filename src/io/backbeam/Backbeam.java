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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.Header;
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
	private String protocol = "http";
	private int port = 80;
	private String env = "pro";
	private String project;
	protected String sharedKey;
	private String secretKey;
	private IntentCallback pushHandler;
	protected GCMCallback gcmCallback;
	private String registrationId;
	private DiskLruCache diskCache;
	
	private String webVersion;
	private String httpAuth;
	
	private BackbeamObject currentUser;
	private String authCode;
	private SocketIO socketio;
	private Map<String, List<RealTimeEventListener>> roomListeners;
	private List<RealTimeConnectionListener> realTimeListeners;
	
	private static Backbeam instance;
	
	private Backbeam() {
		roomListeners = new HashMap<String, List<RealTimeEventListener>>();
		realTimeListeners = new ArrayList<RealTimeConnectionListener>();
		Logger log = Logger.getLogger("io.socket");
		log.setLevel(Level.OFF);
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
	
	public static void setProtocol(String protocol) {
		instance().protocol = protocol.toLowerCase();
		if (instance().protocol.equals("http")) {
			instance().port = 80;
		} else if (instance().protocol.equals("https")) {
			instance().port = 443;
		}
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
	
	public static void setWebVersion(String webVersion) {
		instance().webVersion = webVersion;
	}
	
	public static void setHttpAuth(String httpAuth) {
		instance().httpAuth = httpAuth;
	}
	
	public static Query select(String entity) {
		return new Query(entity);
	}
	
	public static void logout() {
		setCurrentUser(null, null);
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
	
	public static void enableRealTime() {
		instance()._enableRealTime();
	}
	
	private void _enableRealTime() {
		if (socketio != null) return;
		reconnect();
	}
	
	public static void disableRealTime() {
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
			listener.realTimeConnectionFailed(e);
		}
	}
	
	private void reconnect() {
		// https://github.com/Gottox/socket.io-java-client
		fireConnecting();
		// TODO: SSL is not working. See https://github.com/Gottox/socket.io-java-client/issues/14
		// String url  = protocol+"://api-"+env+"-"+project+"."+host+":"+port;
		String url = "http://api-"+env+"-"+project+"."+host+":"+(port == 443 ? 80 : port);
		try {
//			if ("https".equals(protocol)) {
//				SocketIO.setDefaultSSLSocketFactory(SSLContext.getInstance("Default"));
//			}
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
					// reconnect();
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

	protected static void setCurrentUser(BackbeamObject obj, String authCode) {
		instance().currentUser = obj;
		if (obj != null && authCode != null) {
			try {
				HashMap<String, Object> map = new HashMap<String, Object>();
				map.put("user", obj);
				map.put("auth", authCode);
				FileOutputStream fos = instance().context.openFileOutput(USER_FILE, Context.MODE_PRIVATE);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(map);
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
					Object object = (BackbeamObject) ois.readObject();
					if (object instanceof BackbeamObject) { // compatibility with old SDK versions
						instance().currentUser = (BackbeamObject) object;
					} else if (object instanceof HashMap) {
						@SuppressWarnings("unchecked")
						HashMap<String, Object> map = (HashMap<String, Object>) object;
						instance().currentUser = (BackbeamObject) map.get("user");
						instance().authCode = (String) map.get("auth");
					}
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
		String auth = json.get("auth").asString();
		Json values = json.get("objects");
		Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
		BackbeamObject object = objects.get(id);
		if (object != null && auth != null) {
			setCurrentUser(object, auth);
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
	
	protected void fillRequestParams(TreeMap<String, Object> params, RequestParams reqParams) {
		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				Collections.sort(list);
				reqParams.put(key, new ArrayList<String>(list));
			} else {
				reqParams.put(key, value.toString());
			}
		}
	}
	
	protected String cacheString(TreeMap<String, Object> params) {
		StringBuilder cacheKeyString = new StringBuilder();
		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				Collections.sort(list);
				for (String string : list) {
					cacheKeyString.append("&"+key+"="+string);
				}
			} else {
				cacheKeyString.append("&"+key+"="+value);
			}
		}

		return cacheKeyString.toString();
	}
	
	protected String signature(TreeMap<String, Object> params) {
		StringBuilder parameterString = new StringBuilder();
		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				Collections.sort(list);
				for (String string : list) {
					parameterString.append("&"+key+"="+string);
				}
			} else {
				parameterString.append("&"+key+"="+value);
			}
		}
		return Utils.hmacSha1(this.secretKey, parameterString.substring(1));
	}
	
	/*
	protected String sign(TreeMap<String, Object> params, RequestParams reqParams, boolean withNonce) {
		params.put("key", this.sharedKey);
		if (withNonce) {
			params.put("time", new Date().getTime()+"");
			params.put("nonce", UUID.randomUUID().toString());
		}
		
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
	*/
	
	protected String composeURL(String path) {
		return protocol+"://api-"+env+"-"+project+"."+host+":"+port+path;
	}
	
	protected void perform(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final RequestCallback callback) {
		String url = composeURL(path);
		
		if (params == null) params = new TreeMap<String, Object>();
		params.put("method", method);
		params.put("path", path);
		params.put("key", this.sharedKey);
		if (this.authCode != null) {
			params.put("auth", this.authCode);
		}
		String cacheKeyString = cacheString(params);
		
		params.put("time", new Date().getTime()+"");
		params.put("nonce", UUID.randomUUID().toString());
		
		RequestParams reqParams = new RequestParams();
		String signature = signature(params);
		reqParams.put("signature", signature);
		fillRequestParams(params, reqParams);
		
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
	
	public static void enableGCM(String senderID, GCMCallback callback) {
    	Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
    	// sets the app name in the intent
    	Context context = instance().context;
    	registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
    	registrationIntent.putExtra("sender", senderID);
    	context.startService(registrationIntent);
    	instance().gcmCallback = callback;
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
	
	private void requestController(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final ControllerRequestCallback callback) {
		String url = null;
		if (webVersion != null) {
			url = protocol+"://web-"+webVersion+"-"+env+"-"+project+"."+host+":"+port;
		} else {
			url = protocol+"://web-"+env+"-"+project+"."+host+":"+port;
		}
		
		if (params == null) params = new TreeMap<String, Object>();
		
		RequestParams reqParams = new RequestParams();
		String cacheKeyString = cacheString(params);
		fillRequestParams(params, reqParams);
		
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
					callback.success(null, null, response, true);
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
			public void onSuccess(int status, Header[] headers, String json) {
		        Json response = Json.loads(json);
		        String auth = null;
		        String user = null;
		        
		        for (Header header : headers) {
		        	if (header.getName().equals("x-backbeam-auth")) {
		        		auth = header.getValue();
		        	} else if (header.getName().equals("x-backbeam-user")) {
		        		user = header.getValue();
		        	}
				}
		        
		        callback.success(auth, user, response, false);
		        
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
		
	    if (this.authCode != null) {
	    	client.addHeader("x-backbeam-auth", this.authCode);
	    }
	    if (this.httpAuth != null) {
	    	client.setBasicAuth(this.project, this.httpAuth);
	    }
	    client.addHeader("x-backbeam-sdk", "android");
		
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
	
	public static void requestJsonFromController(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final RequestCallback callback) {
		instance().requestController(method, path, params, policy, new ControllerRequestCallback() {
			
			@Override
			public void success(String auth, String user, Json json, boolean fromCache) {
				if (auth != null && user != null) {
					if (auth.length() == 0) {
						logout();
					} else {
						BackbeamObject _user = new BackbeamObject("user", user);
						setCurrentUser(_user, auth);
					}
				}
				
				callback.success(json, fromCache);
				
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public static void requestObjectsFromController(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final FetchCallback callback) {
		instance().requestController(method, path, params, policy, new ControllerRequestCallback() {
			
			@Override
			public void success(String auth, String user, Json json, boolean fromCache) {
		        Json values = json.get("objects");
		        Json ids    = json.get("ids");
				Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
				
				if (auth != null && user != null) {
					if (auth.length() == 0) {
						logout();
					} else {
						BackbeamObject _user = objects.get(user);
						if (_user == null) {
							_user = new BackbeamObject("user", user);
						}
						setCurrentUser(_user, auth);
					}
				}
				
		        List<BackbeamObject> list = new ArrayList<BackbeamObject>(ids.size());
		        for (Json id : ids) {
		        	BackbeamObject obj = objects.get(id.asString());
		        	list.add(obj);
		        }
		        callback.success(list, json.get("count").asInt(), fromCache);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
}
