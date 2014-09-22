package io.backbeam;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.jakewharton.disklrucache.DiskLruCache;
import com.koushikdutta.async.http.socketio.Acknowledge;
import com.koushikdutta.async.http.socketio.ConnectCallback;
import com.koushikdutta.async.http.socketio.DisconnectCallback;
import com.koushikdutta.async.http.socketio.ErrorCallback;
import com.koushikdutta.async.http.socketio.EventCallback;
import com.koushikdutta.async.http.socketio.JSONCallback;
import com.koushikdutta.async.http.socketio.SocketIOClient;
import com.koushikdutta.async.http.socketio.StringCallback;
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
	private Json oldRegistrationIds;
	private DiskLruCache diskCache;
	
	private String webVersion;
	private String httpAuth;
	
	private BackbeamObject currentUser;
	private String authCode;
	private SocketIOClient socketio;
	private int retry = 0;
	private Map<String, List<RealTimeEventListener>> roomListeners;
	private List<RealTimeConnectionListener> realTimeListeners;
	
	private static Backbeam instance;
	
	private Backbeam() {
		oldRegistrationIds = Json.list();
		roomListeners = new HashMap<String, List<RealTimeEventListener>>();
		realTimeListeners = new ArrayList<RealTimeConnectionListener>();
		Logger log = Logger.getLogger("com.codebutler");
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
	
	private JSONArray socketioMessage(TreeMap<String, Object> params) {
		JSONObject object = new JSONObject(params);
		ArrayList<JSONObject> arrayList = new ArrayList<JSONObject>();
		arrayList.add(object);
		JSONArray array = new JSONArray(arrayList);
		return array;
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
	
	public void _unsubscribeFromAllRealTimeEvents() {
		roomListeners.clear();
		if (socketio != null && socketio.isConnected()) {
			TreeMap<String, Object> params = new TreeMap<String, Object>();
			socketio.emit("unsubscribe-all", socketioMessage(params));
		}
	}
	
	public static void unsubscribeFromAllRealTimeEvents() {
		instance()._unsubscribeFromAllRealTimeEvents();
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
		if (socketio != null) {
			if (socketio.isConnected()) return;
			socketio = null;
		}
		reconnect();
	}
	
	public static void disableRealTime() {
		instance()._disableRealTime();
	}
	
	private void _disableRealTime() {
		if (socketio != null) {
			SocketIOClient client = socketio;
			socketio = null;
			client.disconnect();
		}
	}
	
	public static void addRealTimeConnectionListener(RealTimeConnectionListener listener) {
		instance().realTimeListeners.add(listener);
	}
	
	public static void removeRealTimeConnectionListener(RealTimeConnectionListener listener) {
		instance().realTimeListeners.remove(listener);
	}
	
	public static void removeAllRealTimeConnectionListeners() {
		instance().realTimeListeners.clear();
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
		// String url  = protocol+"://api-"+env+"-"+project+"."+host+":"+port;
		String uri = "http://api-"+env+"-"+project+"."+host+":"+(port == 443 ? 80 : port);
		SocketIOClient.connect(uri, new ConnectCallback() {
			
			@Override
			public void onConnectCompleted(Exception ex, SocketIOClient client) {
				if (ex != null) {
					fireFailed(ex);
					return;
				}
				if (socketio != null) {
					socketio.disconnect(); // if there is a previous instance
				}
				retry = 0;
				socketio = client;
				
				client.setDisconnectCallback(new DisconnectCallback() {
					
					@Override
					public void onDisconnect(Exception e) {
						fireDisconnected();
					}
				});
				
				client.setErrorCallback(new ErrorCallback() {
					
					@Override
					public void onError(String error) {
						fireFailed(new BackbeamException(error));
					}
				});
				
				client.addListener("msg", new EventCallback() {
					
					@Override
					public void onEvent(String event, JSONArray args,
							Acknowledge acknowledge) {
						
						try {
							if (args.length() > 0 && args.getJSONObject(0) != null) {
								JSONObject message = args.getJSONObject(0);
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
							}
						} catch(JSONException e) {
							// ignore, wrong message (i.e. not room)
						}
					}
				});
				
				client.setJSONCallback(new JSONCallback() {
					
					@Override
					public void onJSON(JSONObject message, Acknowledge acknowledge) {
						// System.out.println("json: "+message);
					}
				});
				
				client.setStringCallback(new StringCallback() {
					
					@Override
					public void onString(String string, Acknowledge acknowledge) {
						// System.out.println("stirng "+string);
					}
				});
				
				for (String room : roomListeners.keySet()) {
					TreeMap<String, Object> params = new TreeMap<String, Object>();
					params.put("room", room);
					client.emit("subscribe", socketioMessage(params));
				}
				
				fireConnected();
			}
		}, new Handler());
	}

	protected static void setCurrentUser(BackbeamObject obj, String authCode) {
		instance().currentUser = obj;
		instance().authCode = authCode;

		if (instance().context == null) {
			// TODO: warn user
			return;
		}

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
					if (dis.available() > 0) {
						instance().registrationId = dis.readUTF();
					}
					while (dis.available() > 0) {
						instance().oldRegistrationIds.add(dis.readUTF());
					}
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
			if (value instanceof Object[]) {
				Object[] arr = (Object[])value;
				List<String> list = new ArrayList<String>(arr.length);
				for (Object o : arr) {
					list.add(o.toString());
				}
				value = list;
			}
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				reqParams.put(key, list);
			} else if (value instanceof InputStream) {
				reqParams.put(key, (InputStream)value, key);
			} else if (value instanceof File) {
				try {
					reqParams.put(key, (File)value);
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			} else if (value instanceof FileUpload) {
				FileUpload upload = (FileUpload)value;
				if (upload.getFile() != null) {
					try {
						reqParams.put(key, upload.getFile(), upload.getMimeType());
					} catch (FileNotFoundException e) {
						throw new RuntimeException(e);
					}
				} else if (upload.getInputStream() != null) {
					reqParams.put(key, upload.getInputStream(), upload.getFilename(), upload.getMimeType());
				}
			} else {
				reqParams.put(key, value.toString());
			}
		}
		reqParams.setHttpEntityIsRepeatable(true);
	}
	
	protected String cacheString(TreeMap<String, Object> params) {
		StringBuilder cacheKeyString = new StringBuilder();
		for (String key : params.keySet()) {
			Object value = params.get(key);
			if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<String> list = (List<String>) value;
				List<String> copy = new ArrayList<String>(list);
				Collections.sort(copy);
				for (String string : copy) {
					cacheKeyString.append("&"+key+"="+string);
				}
			} else if (value instanceof InputStream || value instanceof File) {
				// ignore
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
				List<String> copy = new ArrayList<String>(list);
				Collections.sort(copy);
				for (String string : copy) {
					parameterString.append("&"+key+"="+string);
				}
			} else if (value instanceof FileUpload) {
				// ignore
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
		    	
				if (oldRegistrationIds.size() > 0) {
					oldRegistrationIds = Json.list();
					Backbeam.storeRegistrationId(instance().registrationId); // saves the file
				}
				
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
		    public void onFailure(Throwable error, String bodyString) {
				try {
					Json json = Json.loads(bodyString);
					Json status = json.get("status");
					if (status != null) {
						Json errorMessage = json.get("errorMessage");
						if (errorMessage == null) {
							callback.failure(new BackbeamException(status.str()));
						} else {
							callback.failure(new BackbeamException(status.str(), errorMessage.str()));
						}
					} else {
						callback.failure(new BackbeamException(error));
					}
				} catch (Exception e) {
					callback.failure(new BackbeamException(error));
					return;
				}
		    }
		    
		    @Override
		    public void onFinish() {
		    }
		};
		
	    if (this.oldRegistrationIds.size() > 0) {
	    	client.addHeader("x-backbeam-old-tokens", oldRegistrationIds.toString());
	    	if (this.registrationId != null) {
		    	client.addHeader("x-backbeam-current-token", "gcm="+this.registrationId);
	    	}
	    }
	    
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
	
	public static String registrationId() {
		return instance().registrationId;
	}
	
	static void storeRegistrationId(String registrationID) {
		String current = instance().registrationId;
		if (current != null && !current.equals(registrationID)) {
			instance().oldRegistrationIds.add(current);
		}
		instance().registrationId = registrationID;
		FileOutputStream fos = null;
		try {
			fos = instance().context.openFileOutput(REGISTRATION_ID_FILE, Context.MODE_PRIVATE);
			DataOutputStream dos = new DataOutputStream(fos);
			if (registrationID != null) {
				dos.writeUTF(registrationID);
			}
			for (Json oldRegistrationId : instance().oldRegistrationIds) {
				dos.writeUTF(oldRegistrationId.str());
			}
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
	
	private static boolean subscriptionToChannels(String path, final OperationCallback callback, String[] channels) {
		String registrationId = instance().registrationId;
		registrationId = "foo";
		if (registrationId == null) return false;
		
		TreeMap<String, Object> params = new TreeMap<String, Object>();
		params.put("gateway", "gcm");
		params.put("token", registrationId);
		if (channels != null) {
			params.put("channels", Arrays.asList(channels));
		}
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
	
	public static boolean unsubscribeFromAllChannels(final OperationCallback callback) {
		return subscriptionToChannels("/push/unsubscribe-all", callback, null);
	}
	
	public static boolean subscribedChannels(final ListCallback callback) {
		String registrationId = instance().registrationId;
		registrationId = "foo";
		if (registrationId == null) return false;
		
		TreeMap<String, Object> params = new TreeMap<String, Object>();
		params.put("gateway", "gcm");
		params.put("token", registrationId);
		instance().perform("GET", "/push/subscribed-channels", params, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
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
				List<String> list = new ArrayList<String>();
				for (Json channel : json.get("channels")) {
					String c = channel.asString();
					if (c != null) {
						list.add(c);
					}
				}
				callback.success(list);
			}
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
		return true;
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
	
	public static void gitHubSignup(String accessToken, String joins, Object[] params, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		socialSignup("github", prms, callback);
	}
	
	public static void gitHubSignup(String accessToken, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		socialSignup("github", prms, callback);
	}
	
	public static void linkedInSignup(String accessToken, String joins, Object[] params, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		socialSignup("linkedin", prms, callback);
	}
	
	public static void linkedInSignup(String accessToken, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		socialSignup("linkedin", prms, callback);
	}
	
	public static void googlePlusSignup(String accessToken, String joins, Object[] params, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		if (joins != null) {
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		socialSignup("googleplus", prms, callback);
	}
	
	public static void googlePlusSignup(String accessToken, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		socialSignup("googleplus", prms, callback);
	}
	
	public static void facebookSignup(String accessToken, String joins, Object[] params, final SignupCallback callback) {
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
	
	public static void facebookSignup(String accessToken, final SignupCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		prms.put("access_token", accessToken);
		socialSignup("facebook", prms, callback);
	}
	
	public static void twitterSignup(String oauthToken, String oauthTokenSecret, String joins, Object[] params, final SignupCallback callback) {
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
	
	public static void twitterSignup(String oauthToken, String oauthTokenSecret, final SignupCallback callback) {
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
			url = "://web-"+webVersion+"-"+env+"-"+project+"."+host+":"+port;
		} else {
			url = "://web-"+env+"-"+project+"."+host+":"+port;
		}
		url += path;
		
		if (params == null) params = new TreeMap<String, Object>();
		RequestParams reqParams = new RequestParams();
		String cacheKeyString = method+"\n"+url+"\n"+cacheString(params);
		fillRequestParams(params, reqParams);
		
		url = protocol+url;
		
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
			byte[] data = null;
			if (diskCache != null) {
				try {
					DiskLruCache.Snapshot snapshot = diskCache.get(cacheKey);
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					InputStream in = snapshot.getInputStream(0);
					byte[] buffer = new byte[1024*10];
					int read = 0;
					while ((read = in.read(buffer)) != -1) {
						out.write(buffer, 0, read);
					}
					data = out.toByteArray();
				} catch (Exception ex) {
					// ignore
					// ex.printStackTrace();
				}
			}
			boolean read = false;
			if (data != null) {
				callback.success(null, null, data, true);
			}
			if (policy == FetchPolicy.LOCAL_ONLY) {
				if (!read) {
					callback.failure(new BackbeamException("CachedDataNotFound"));
				}
				return;
			}
		}
		
		final String _cacheKey = cacheKey;
		
		AsyncHttpClient client = new AsyncHttpClient();
		AsyncHttpResponseHandler handler = new AsyncHttpResponseHandler() {
			
			@Override
			public void onSuccess(int status, Header[] headers, byte[] body) {
				
				if (oldRegistrationIds.size() > 0) {
					oldRegistrationIds = Json.list();
					Backbeam.storeRegistrationId(instance().registrationId); // saves the file
				}
				
		        String auth = null;
		        String user = null;
		        
		        for (Header header : headers) {
		        	if (header.getName().equals("x-backbeam-auth")) {
		        		auth = header.getValue();
		        	} else if (header.getName().equals("x-backbeam-user")) {
		        		user = header.getValue();
		        	}
				}
		        
		        callback.success(auth, user, body, false);
		        
		        if (useCache) {
		            try {
						DiskLruCache.Editor editor = diskCache.edit(_cacheKey);
						OutputStream out = editor.newOutputStream(0);
						out.write(body);
						editor.commit();
						diskCache.flush();
					} catch (IOException e) {
						e.printStackTrace(); // TODO: handle error
					}
		        }
		    }
		    
		    @Override
		    public void onFailure(int statusCode, Header[] headers, byte[] body, Throwable error) {
				Json json = null;
				if (body != null) {
					String bodyString;
					try {
						bodyString = new String(body, "UTF-8");
						json = Json.loads(bodyString);
						Json status = json.get("status");
						if (status != null) {
							Json errorMessage = json.get("errorMessage");
							if (errorMessage == null) {
								callback.failure(new BackbeamException(status.str()));
							} else {
								callback.failure(new BackbeamException(status.str(), errorMessage.str()));
							}
						} else {
							callback.failure(new BackbeamException(error));
						}
					} catch (Exception e) {
						callback.failure(new BackbeamException(error));
						return;
					}
				} else {
					callback.failure(new BackbeamException(error));
				}
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
	    if (this.oldRegistrationIds.size() > 0) {
	    	client.addHeader("x-backbeam-old-tokens", oldRegistrationIds.toString());
	    	if (this.registrationId != null) {
		    	client.addHeader("x-backbeam-current-token", "gcm="+this.registrationId);
	    	}
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
	
	public static void requestDataFromController(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final RequestDataCallback callback) {
		instance().requestController(method, path, params, policy, new ControllerRequestCallback() {
			
			@Override
			public void success(String auth, String user, byte[] body, boolean fromCache) {
				if (auth != null && user != null) {
					if (auth.length() == 0) {
						logout();
					} else {
						BackbeamObject _user = new BackbeamObject("user", user);
						setCurrentUser(_user, auth);
					}
				}
				
				callback.success(body, fromCache);
				
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public static void requestJsonFromController(String method, String path, TreeMap<String, Object> params, FetchPolicy policy, final RequestCallback callback) {
		instance().requestController(method, path, params, policy, new ControllerRequestCallback() {
			
			@Override
			public void success(String auth, String user, byte[] body, boolean fromCache) {
				if (auth != null && user != null) {
					if (auth.length() == 0) {
						logout();
					} else {
						BackbeamObject _user = new BackbeamObject("user", user);
						setCurrentUser(_user, auth);
					}
				}
				
				Json json = null;
				if (body != null) {
					String bodyString;
					try {
						bodyString = new String(body, "UTF-8");
						json = Json.loads(bodyString);
					} catch (Exception e) {
						callback.failure(new BackbeamException(e));
						return;
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
			public void success(String auth, String user, byte[] body, boolean fromCache) {
				Json json = null;
				if (body != null) {
					String bodyString;
					try {
						bodyString = new String(body, "UTF-8");
						json = Json.loads(bodyString);
					} catch (Exception e) {
						callback.failure(new BackbeamException(e));
						return;
					}
				} else { // empty response
					callback.failure(new BackbeamException("Empty server response"));
					return;
				}
				
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
