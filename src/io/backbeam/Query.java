package io.backbeam;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Query {
	
	private String entity;
	private String query;
	private Object[] params;
	private FetchPolicy fetchPolicy;
	
	public Query(String entity) {
		this.entity = entity;
		this.fetchPolicy = FetchPolicy.REMOTE_ONLY;
	}
	
	public Query setQuery(String query, Object... params) {
		this.query = query;
		this.params = params;
		return this;
	}
	
	public void fetch(int limit, int offset, final FetchCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		if (query != null) {
			prms.put("q", query);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		prms.put("limit", Integer.toString(limit));
		prms.put("offset", Integer.toString(offset));
		Backbeam.instance().perform("GET", "/data/"+entity, prms, fetchPolicy, new RequestCallback() {
			@Override
			public void success(Json response, boolean fromCache) {
		        Json values = response.get("objects");
		        Json ids    = response.get("ids");
		        
		        Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
		        List<BackbeamObject> list = new ArrayList<BackbeamObject>(ids.size());
		        for (Json id : ids) {
		        	BackbeamObject obj = objects.get(id.asString());
		        	list.add(obj);
		        }
		        callback.success(list, response.get("count").asInt(), fromCache);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public void near(String field, double lat, double lon, int limit, final FetchCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		if (query != null) {
			prms.put("q", query);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		prms.put("lat", Double.toString(lat));
		prms.put("lon", Double.toString(lon));
		prms.put("limit", Integer.toString(limit));
		Backbeam.instance().perform("GET", "/data/"+entity+"/near/"+field, prms, fetchPolicy, new RequestCallback() {
			@Override
			public void success(Json response, boolean fromCache) {
		        Json values = response.get("objects");
		        Json ids    = response.get("ids");
		        
		        Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
		        List<BackbeamObject> list = new ArrayList<BackbeamObject>(ids.size());
		        for (Json id : ids) {
		        	BackbeamObject obj = objects.get(id.asString());
		        	list.add(obj);
		        }
		        callback.success(list, response.get("count").asInt(), fromCache);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public void bounding(String field, double swlat, double swlon, double nelat, double nelon, int limit, final FetchCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		if (query != null) {
			prms.put("q", query);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		prms.put("swlat", Double.toString(swlat));
		prms.put("swlon", Double.toString(swlon));
		prms.put("nelat", Double.toString(nelat));
		prms.put("nelon", Double.toString(nelon));
		prms.put("limit", Integer.toString(limit));
		Backbeam.instance().perform("GET", "/data/"+entity+"/bounding/"+field, prms, fetchPolicy, new RequestCallback() {
			@Override
			public void success(Json response, boolean fromCache) {
		        Json values = response.get("objects");
		        Json ids    = response.get("ids");
		        
		        Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
		        List<BackbeamObject> list = new ArrayList<BackbeamObject>(ids.size());
		        for (Json id : ids) {
		        	BackbeamObject obj = objects.get(id.asString());
		        	list.add(obj);
		        }
		        callback.success(list, response.get("count").asInt(), fromCache);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}

	public FetchPolicy getFetchPolicy() {
		return fetchPolicy;
	}

	public void setFetchPolicy(FetchPolicy fetchPolicy) {
		this.fetchPolicy = fetchPolicy;
	}
	
}
