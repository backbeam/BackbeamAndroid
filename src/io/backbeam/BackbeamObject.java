package io.backbeam;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.loopj.android.http.RequestParams;

public class BackbeamObject implements Serializable {

	private static final long serialVersionUID = 8296458723947296629L;
	
	private String entity;
	private String id;
	private Date createdAt;
	private Date updatedAt;
	private Hashtable<String, Object> fields;
	private Hashtable<String, String> loginData;
	
	private transient TreeMap<String, Object> changes;
	
	public BackbeamObject(String entity) {
		this.entity = entity;
		this.fields = new Hashtable<String, Object>();
		changes = new TreeMap<String, Object>();
	}

	public BackbeamObject(String entity, String id) {
		this.entity = entity;
		this.fields = new Hashtable<String, Object>();
		this.id = id;
		changes = new TreeMap<String, Object>();
	}
	
	public BackbeamObject(String entity, Json obj, Map<String, BackbeamObject> references, String id) {
		this(entity);
		this.id = id;
		fillValues(obj, references);
	}
	
	public static Map<String, BackbeamObject> objectsFromValues(Json values, Map<String, BackbeamObject> objects) {
		if (objects == null) {
			objects = new HashMap<String, BackbeamObject>();
		}
		for (String id : values.keys()) {
			BackbeamObject obj = objects.get(id);
			if (obj != null) continue;
			String type = values.get(id).get("type").asString();
			objects.put(id, new BackbeamObject(type, id));
		}
		for (String id : values.keys()) {
			BackbeamObject obj = objects.get(id);
			Json value = values.get(id);
			obj.fillValues(value, objects);
		}
		return objects;
	}
	
	private void fillValues(Json obj, Map<String, BackbeamObject> references) {
		changes = new TreeMap<String, Object>();
		for (String key : obj.keys()) {
			if (key.equals("created_at")) {
				this.createdAt = new Date(obj.get(key).asLong());
			} else if (key.equals("updated_at")) {
				this.updatedAt = new Date(obj.get(key).asLong());
			} else if (key.equals("type")) {
				this.entity = obj.get(key).asString();
			} else if (key.startsWith("login_")) {
				String _key = key.substring("login_".length());
				if (loginData == null) {
					loginData = new Hashtable<String, String>();
				}
				loginData.put(_key, obj.get(key).str());
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
						Json _id = val.get("id");
						Json _type = val.get("type");
						if (_id != null && _type != null) {
							value = references.get(_id);
							if (value == null) {
								value = new BackbeamObject(_type.str(), _id.str());
							}
						} else {
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
						}
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
	
	public String loginDataForProvider(String provider, String key) {
		if (loginData == null) return null;
		String _key = provider+"_"+key;
		return loginData.get(_key);
	}
	
	public String getFacebookData(String key) {
		return loginDataForProvider("fb", key);
	}

	public String getTwitterData(String key) {
		return loginDataForProvider("tw", key);
	}
	
	public Date getDate(String field) {
		Object o = fields.get(field);
		if (o instanceof Date) return (Date) o;
		return null;
	}
	
	public void setDate(String field, Date date) {
		fields.put(field, date);
		changes.put("set-"+field, ""+date.getTime());
	}
	
	public Object getRawValue(String field) {
		return fields.get(field);
	}
	
	public void setRawValue(String field, Object value) {
		if (value instanceof String) {
			setString(field, (String) value);
		} else if (value instanceof Number) {
			setNumber(field, (Number) value);
		} else if (value instanceof Date) {
			setDate(field, (Date) value);
		} else if (value instanceof Location) {
			setLocation(field, (Location) value);
		} else if (value instanceof BackbeamObject) {
			setObject(field, (BackbeamObject) value);
		}
	}
	
	public String getString(String field) {
		Object o = fields.get(field);
		if (o instanceof String) return (String) o;
		return null;
	}
	
	public Number getNumber(String field) {
		Object o = fields.get(field);
		if (o instanceof Number) return (Number) o;
		return null;
	}
	
	public void setString(String field, String value) {
		fields.put(field, value);
		changes.put("set-"+field, value);
	}
	
	public BackbeamObject getObject(String field) {
		Object o = fields.get(field);
		if (o instanceof BackbeamObject) return (BackbeamObject) o;
		return null;
	}
	
	public void setObject(String field, BackbeamObject object) {
		fields.put(field, object);
		changes.put("set-"+field, object.getId());
	}
	
	public void removeField(String field) {
		String command = "del-"+field;
		fields.remove(field);
		changes.put(command, "-");
	}
	
	public void addObject(String field, BackbeamObject object) {
		String key = "add-"+field;
		@SuppressWarnings("unchecked")
		List<String> values = (List<String>) changes.get(key);
		if (values == null) {
			values = new ArrayList<String>();
			changes.put(key, values);
		}
		values.add(object.getId());
	}
	
	public void removeObject(String field, BackbeamObject object) {
		String key = "rem-"+field;
		@SuppressWarnings("unchecked")
		List<String> values = (List<String>) changes.get(key);
		if (values == null) {
			values = new ArrayList<String>();
			changes.put(key, values);
		}
		values.add(object.getId());
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
		changes.put("set-"+field, value);
	}
	
	public void setNumber(String field, Number value) {
		fields.put(field, value);
		changes.put("set-"+field, value);
	}
	
	// TODO: support many increments without overriding previous command
	public void incrementNumber(String field, int value) {
		Number n = getNumber(field);
		if (n != null) {
			n = n.intValue() + value;
		} else {
			n = value;
		}
		fields.put(field, n);
		String command = "incr-"+field;
		changes.put(n.toString(), command);
	}
	
	public Set<String> getFieldNames() {
		return fields.keySet();
	}
	
	public void save(final ObjectCallback callback) {
		String path = id == null ? "/data/"+entity : "/data/"+entity+"/"+id;
		final String method = id == null ? "POST" : "PUT";
		
		final BackbeamObject obj = this;
		Backbeam.instance().perform(method, path, changes, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json json, boolean fromCache) {
				obj.changes = new TreeMap<String, Object>();
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json auth     = json.get("auth");
				Json values   = json.get("objects");
				String id     = json.get("id").asString();
				if (status == null || values == null || id == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				obj.id = id;
				Map<String, BackbeamObject> objects = new HashMap<String, BackbeamObject>();
				objects.put(id, obj);
				BackbeamObject.objectsFromValues(values, objects);
				if (entity.equals("user")) {
					fields.remove("password");
				}
				if (entity.equals("user") && method.equals("POST")) {
					Backbeam.logout();
					if (status.equals("Success") && auth != null) {
						Backbeam.setCurrentUser(obj, auth.asString());
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
		Backbeam.instance().perform(method, path, null, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			@Override
			public void success(Json json, boolean fromCache) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json values   = json.get("objects");
				if (status == null || values == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				Map<String, BackbeamObject> objects = new HashMap<String, BackbeamObject>();
				objects.put(id, obj);
				BackbeamObject.objectsFromValues(values, objects);
				callback.success(obj);
			}
			
			@Override
			public void failure(BackbeamException exception) {
				callback.failure(exception);
			}
		});
	}
	
	public void refresh(final ObjectCallback callback) {
		refresh(null, null, callback);
	}
	
	public void refresh(String joins, Object[] params, final ObjectCallback callback) {
		String path = "/data/"+entity+"/"+id;
		String method = "GET";
		
		TreeMap<String, Object> prms = null;
		if (joins != null) {
			prms = new TreeMap<String, Object>();
			prms.put("joins", joins);
			if (params != null) {
				prms.put("params", Utils.stringsFromParams(params));
			}
		}
		
		final BackbeamObject obj = this;
		Backbeam.instance().perform(method, path, prms, FetchPolicy.REMOTE_ONLY, new RequestCallback() {
			public void success(Json json, boolean fromCache) {
				if (!json.isMap()) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				String status = json.get("status").asString();
				Json values   = json.get("objects");
				if (status == null || values == null) {
					callback.failure(new BackbeamException("InvalidResponse"));
					return;
				}
				Map<String, BackbeamObject> objects = new HashMap<String, BackbeamObject>();
				objects.put(id, obj);
				BackbeamObject.objectsFromValues(values, objects);
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
	
	public boolean isEmpty() {
	    return this.createdAt == null;
	}

	public boolean idDirty() {
	    return this.changes.size() > 0;
	}

	public boolean isNew() {
	    return this.id == null;
	}
	
	public String composeFileURL(TreeMap<String, Object> params) {
		String path = "/data/file/download/"+this.id+"/"+getNumber("version");
		if (params == null) {
			params = new TreeMap<String, Object>();
		}
		
		params.put("method", "GET");
		params.put("path", path);
		params.put("key", Backbeam.instance().sharedKey);
		
		RequestParams reqParams = new RequestParams();
		String signature = Backbeam.instance().signature(params);
		reqParams.put("signature", signature);

		StringBuilder builder = new StringBuilder(Backbeam.instance().composeURL(path));
		builder.append("?");
		boolean first = true;
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			if (!first) {
				builder.append("&");
			}
			builder.append(entry.getKey());
			builder.append("=");
			builder.append(Utils.urlEncode(entry.getValue().toString()));
			first = false;
		}
		return builder.toString();
	}

}
