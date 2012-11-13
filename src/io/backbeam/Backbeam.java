package io.backbeam;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class Backbeam {
	
	private String host = "backbeam.io";
	private int port = 80;
	private String env  = "pro";
	private String project;
	private String twitterConsumerKey;
	private String twitterConsumerSecret;
	
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
	
	public static void subscribeToChannels(String... channels) {
		
	}
	
	public static void subscribeToChannels(OperationCallback callback, String... channels) {
		
	}
	
	public static void sendPushNotificationToChannel(PushNotification notification, String channel, OperationCallback callback) {
		
	}
	
	public static void logout() {
		setCurrentUser(null);
	}
	
	public static BackbeamObject currentUser() {
		return instance().currentUser;
	}

	protected static void setCurrentUser(BackbeamObject obj) {
		instance().currentUser = obj;
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
		System.out.println("url = "+url);
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
	
}
