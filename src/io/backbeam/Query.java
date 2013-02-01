package io.backbeam;

import java.util.ArrayList;
import java.util.Date;
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
		        Json values = response.get("objects");
		        Json ids    = response.get("ids");
		        
		        Map<String, BackbeamObject> objects = BackbeamObject.objectsFromValues(values, null);
		        List<BackbeamObject> list = new ArrayList<BackbeamObject>(ids.size());
		        for (Json id : ids) {
		        	BackbeamObject obj = objects.get(id.asString());
		        	list.add(obj);
		        }
		        callback.success(list);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
}
