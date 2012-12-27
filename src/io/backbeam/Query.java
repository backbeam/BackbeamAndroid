package io.backbeam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Query {
	
	private String entity;
	private String query;
	private String[] params;
	
	public Query(String entity) {
		this.entity = entity;
	}
	
	public Query setQuery(String query, String... params) {
		this.query = query;
		this.params = params; // TODO convert objects, such as dates, etc.
		return this;
	}
	
	private String stringFromObject(Object obj, boolean addEntity) {
		if (obj instanceof String) {
			return (String) obj;
		} else if (obj instanceof BackbeamObject) {
			BackbeamObject o = (BackbeamObject) obj;
			if (addEntity) {
				return o.getEntity()+"/"+o.getId();
			} else {
				return o.getId();
			}
		} else if (obj instanceof Date) {
			return ""+((Date) obj).getTime();
		} else if (obj instanceof Location) {
			Location location = (Location) obj;
			String s = location.getLatitude()+","+location.getLongitude()+","+location.getAltitude()+"|";
			if (location.getAddress() != null) s += location.getAddress();
			return s;
		}
		return null;
	}
	
	public void fetch(int limit, int offset, final FetchCallback callback) {
		TreeMap<String, Object> prms = new TreeMap<String, Object>();
		if (query != null) {
			prms.put("q", query);
		}
		prms.put("limit", Integer.toString(limit));
		prms.put("offset", Integer.toString(offset));
		if (params != null) {
			List<String> list = new ArrayList<String>();
			for (Object obj : params) {
				list.add(stringFromObject(obj, true));
			}
			prms.put("params", list);
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
