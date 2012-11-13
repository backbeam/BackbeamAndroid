package io.backbeam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.loopj.android.http.RequestParams;

public class Query {
	
	private String entity;
	private String query;
	private Object[] params;
	
	public Query(String entity) {
		this.entity = entity;
	}
	
	public Query setQuery(String query, Object... params) {
		this.query = query;
		this.params = params;
		return this;
	}
	
	public void fetch(int limit, int offset, final FetchCallback callback) {
		RequestParams prms = new RequestParams();
		if (query != null) {
			prms.put("q", query);
		}
		prms.put("limit", Integer.toString(limit));
		prms.put("offset", Integer.toString(offset));
		if (params != null && params.length > 0) {
			prms.put("params", (String)params[0]);
		}
		Backbeam.instance().perform("GET", "/data/"+entity, prms, new RequestCallback() {
			@Override
			public void success(Json response) {
		        Json objs = response.get("objects");
		        Json refs = response.get("references");
		        
		        Map<String, BackbeamObject> references = new HashMap<String, BackbeamObject>(refs.size());
		        for (String id : refs.keys()) {
		        	Json obj = refs.get(id);
		        	BackbeamObject object = new BackbeamObject(entity, obj, null, id);
		        	object.setEntity(obj.get("type").asString());
		        	references.put(id, object);
		        }
		        
		        List<BackbeamObject> objects = new ArrayList<BackbeamObject>(objs.size());
		        for (Json obj : objs) {
		        	BackbeamObject object = new BackbeamObject(entity, obj, references, null);
		        	objects.add(object);
		        }
		        
		        callback.success(objects);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
}
