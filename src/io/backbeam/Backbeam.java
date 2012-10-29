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
