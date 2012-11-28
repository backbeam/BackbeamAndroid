package io.backbeam;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.loopj.android.http.RequestParams;

public class BackbeamObject implements Serializable {

	private static final long serialVersionUID = 8296458723947296629L;
	
	private String entity;
	private String id;
	private Date createdAt;
	private Date updatedAt;
	private Hashtable<String, Object> fields;
	
	private transient RequestParams changes;
	
	public BackbeamObject(String entity) {
		this.entity = entity;
		this.fields = new Hashtable<String, Object>();
		changes = new RequestParams();
	}

	public BackbeamObject(String entity, String id) {
		this.entity = entity;
		this.fields = new Hashtable<String, Object>();
		this.id = id;
		changes = new RequestParams();
	}
	
	public BackbeamObject(String entity, Json obj, Map<String, BackbeamObject> references, String id) {
		this(entity);
		this.id = id;
		fillValues(obj, references);
	}
	
	private void fillValues(Json obj, Map<String, BackbeamObject> references) {
		changes = new RequestParams();
		for (String key : obj.keys()) {
			if (key.equals("id")) {
				this.id = obj.get(key).asString();
			} else if (key.equals("created_at")) {
				this.createdAt = new Date(obj.get(key).asLong());
			} else if (key.equals("updated_at")) {
				this.updatedAt = new Date(obj.get(key).asLong());
			} else {
				int i = key.indexOf('#');
				if (i > 0) {
					String field = key.substring(0, i);
					String type = key.substring(i+1);
					Json val = obj.get(key);
					Object value = val.getObject();
					if (type.equals("d")) {
						value = new Date(val.asLong());
					} else if (type.equals("l") && val.isMap()) {
						Location location = new Location();
						Json lat = val.get("lat");
						Json lon = val.get("lon");
						if (lat != null && lon != null) {
							location.setLatitude(lat.asDouble());
							location.setLongitude(lon.asDouble());
						}
						Json alt = val.get("alt");
						if (alt != null) {
							location.setAltitude(alt.asDouble());
						}
						Json addr = val.get("addr");
						if (addr != null) {
							location.setAddress(addr.asString());
						}
						value = location;
					} else if (type.equals("r") && val.isMap()) {
						Json ids = val.get("result");
						List<BackbeamObject> results = new ArrayList<BackbeamObject>(ids.size());
						for (Json o : ids) {
							BackbeamObject reference = references.get(o.asString());
							if (reference != null) {
								results.add(reference);
							}
						}
						JoinResult result = new JoinResult(val.get("count").asInt(), results);
						value = result;
					} else if (type.equals("r") && val.isString()) {
						value = references.get(val.asString());
					}
					if (value != null) {
						fields.put(field, value);
					}
				}
			}
		}
	}

	public Date getDate(String field) {
		Object o = fields.get(field);
		if (o instanceof Date) return (Date) o;
		return null;
	}
	
	public void setDate(String field, Date date) {
		fields.put(field, date);
		changes.add("set-"+field, ""+date.getTime());
	}
	
	public String getString(String field) {
		Object o = fields.get(field);
		if (o instanceof String) return (String) o;
		return null;
	}
	
	public void setString(String field, String value) {
		fields.put(field, value);
		changes.add("set-"+field, value);
	}
	
	public BackbeamObject getObject(String field) {
		Object o = fields.get(field);
		if (o instanceof BackbeamObject) return (BackbeamObject) o;
		return null;
	}
	
	public void setObject(String field, BackbeamObject object) {
		fields.put(field, object);
		changes.add("set-"+field, object.getId());
	}
	
	public void addObject(String field, BackbeamObject object) {
		// TODO: added list?
		changes.add("add-"+field, object.getId());
	}
	
	public void removeObject(String field, BackbeamObject object) {
		// TODO: removed list?
		changes.add("rem-"+field, object.getId());
	}
	
	public JoinResult getJoinResult(String field) {
		Object o = fields.get(field);
		if (o instanceof JoinResult) return (JoinResult) o;
		return null;
	}
	
	public Location getLocation(String field) {
		Object o = fields.get(field);
		if (o instanceof Location) return (Location) o;
		return null;
	}
	
	public void setLocation(String field, Location location) {
		fields.put(field,  location);
		String value = location.getLatitude()+","+location.getLongitude()+","+location.getAltitude()+"|";
		if (location.getAddress() != null) {
			value += location.getAddress();
		}
		changes.add("set-"+field, value);
	}
	
	public void save(final ObjectCallback callback) {
		final RequestParams params = changes;
		String path = id == null ? "/data/"+entity : "/data/"+entity+"/"+id;
		final String method = id == null ? "POST" : "PUT";
		
		final BackbeamObject obj = this;
		Backbeam.instance().perform(method, path, params, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json object = json.get("object");
				if (status == null || object == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				fillValues(object, null);
				if (entity.equals("user")) {
					fields.remove("password");
				}
				if (entity.equals("user") && method.equals("POST")) {
					Backbeam.logout();
					if (status.equals("Success")) {
						Backbeam.setCurrentUser(obj);
					}
				}
				callback.success(obj);
			}
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public void remove(final ObjectCallback callback) {
		String path = "/data/"+entity+"/"+id;
		String method = "DELETE";
		
		final BackbeamObject obj = this;
		Backbeam.instance().perform(method, path, null, new RequestCallback() {
			@Override
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json object = json.get("object");
				if (status == null || object == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				fillValues(object, null);
				callback.success(obj);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public void refresh(final ObjectCallback callback) {
		String path = "/data/"+entity+"/"+id;
		String method = "GET";
		
		final BackbeamObject obj = this;
		Backbeam.instance().perform(method, path, null, new RequestCallback() {
			public void success(Json json) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json object = json.get("object");
				if (status == null || object == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				fillValues(object, null);
				callback.success(obj);
			}
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public String getEntity() {
		return entity;
	}
	
	public String getId() {
		return id;
	}
	
	public Date getCreatedAt() {
		return createdAt;
	}
	
	public Date getUpdatedAt() {
		return updatedAt;
	}

	void setEntity(String entity) {
		this.entity = entity;
	}

}
