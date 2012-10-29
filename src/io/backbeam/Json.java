package io.backbeam;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

class JsonException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public JsonException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public JsonException(String detailMessage) {
		super(detailMessage);
	}

	public JsonException(Throwable throwable) {
		super(throwable);
	}
	
}

public class Json implements Iterable<Json> {
	
	private Object object;
	private Map<String, Json> map;
	private List<Json> list;
	
	public Json(Object object) {
		this.object = object;
		
		if(object == null) return;
		if(object instanceof Number) return;
		if(object instanceof String) return;
		if(object instanceof Boolean) return;
		
		throw new IllegalArgumentException("Unsupported type: " + object.getClass().getName());
	}
	
	private Json() {
	}
	
	public static Json map() {
		Json json = new Json();
		json.map = new HashMap<String, Json>();
		return json;
	}
	
	public static Json sortedMap() {
		Json json = new Json();
		json.map = new TreeMap<String, Json>();
		return json;
	}
	
	public static Json list(Object...objects) {
		Json json = new Json();
		json.list = new ArrayList<Json>();
		json.add(objects);
		return json;
	}
	
	private Json wrap(Object object) {
		if(object instanceof Json)
			return (Json) object;
		return new Json(object);
	}
	
	public Json add(Object...objects) {
		for (Object object : objects) {
			list.add(wrap(object));
		}
		return this;
	}
	
	public Json addAt(int index, Object object) {
		list.add(index, wrap(object));
		return this;
	}
	
	public Json removeAt(int index) {
		list.remove(index);
		return this;
	}
	
	public int indexOf(Object value) {
		return list.indexOf(wrap(value));
	}
	
	public Json get(String key) {
		return map.get(key);
	}
	
	public Json put(String key, Object value) {
		map.put(key, wrap(value));
		return this;
	}
	
	public Json putAll(Json json) {
		Set<String> keys = json.keys();
		for (String key : keys) {
			put(key, json.get(key));
		}
		return this;
	}
	
	public Json addAll(Json json) {
		for (Json js : json) {
			add(js);
		}
		return this;
	}
	
	public Json removeAll(Json json) {
		if(json.isList()) {
			for (Json js : json) {
				remove(js);
			}
		} else if(json.isMap()) {
			for (String key : json.keys()) {
				remove(key);
			}
		}
		return this;
	}
	
	public Json at(int index) {
		// it prevents from generating any exception in case the model has changed dynamically
		// by adding fields to the json embedded list
		if(index >= list.size())
			return new Json();
		return list.get(index);
	}
	
	public Set<String> keys() {
		return map.keySet();
	}
	
	public boolean containsKey(String key) {
		return map.containsKey(key);
	}
	
	public boolean containsValue(Object obj) {
		return map.containsValue(wrap(obj));
	}
	
	public boolean contains(Object obj) {
		return list.contains(wrap(obj));
	}
	
	public Collection<Json> values() {
		return map.values();
	}
	
	public boolean remove(Object obj) {
		if(list != null) return list.remove(wrap(obj));
		if(map != null) return map.remove(obj) != null;
		return false;
	}
	
	public void sumIntegers(Json other) {
		for (String key : other.keys()) {
			Json value = get(key);
			if(value == null) {
				put(key, other.get(key));
			} else {
				put(key, value.asLong()+other.get(key).asLong());
			}
		}
	}
	
	public void sumReals(Json other) {
		for (String key : other.keys()) {
			Json value = get(key);
			if(value == null) {
				put(key, other.get(key));
			} else {
				put(key, value.asDouble()+other.get(key).asDouble());
			}
		}
	}
	
	public void sumInteger(String key, long value) {
		if(containsKey(key)) {
			value += get(key).asLong();
		}
		put(key, value);
	}
	
	public void sumReal(String key, double value) {
		if(containsKey(key)) {
			value += get(key).asDouble();
		}
		put(key, value);
	}
	
	public void putDefault(String key, Object value) {
		if(!containsKey(key))
			put(key, value);
	}
	
	public int size() {
		if(map != null) return map.size();
		if(list != null) return list.size();
		return 1;
	}
	
	public Iterator<Json> iterator() {
		if(list != null) return list.iterator();
		if(map != null) return map.values().iterator();
		return null;
	}
	
	public boolean isEmpty() {
		return size() == 0;
	}
	
	public Object getObject() {
		return object;
	}
	
	private void formatString(Writer writer, String s) throws IOException {
		writer.write('\"');
		for(int i=0; i<s.length(); i++) {
			char c = s.charAt(i);
			switch(c) {
			case '\\':
			case '\"':
			case '/':
				writer.write('\\');
				writer.write(c);
				break;
			case '\b':
				writer.write("\\b");
				break;
			case '\f':
				writer.write("\\f");
				break;
			case '\n':
				writer.write("\\n");
				break;
			case '\r':
				writer.write("\\r");
				break;
			case '\t':
				writer.write("\\t");
				break;
			default:
				writer.write(c);
			}
		}
		writer.write("\"");
	}
	
	public void format(Writer writer, Object o) throws IOException {
		if(o instanceof String)
			formatString(writer, (String) o);
		else
			writer.write(o.toString());
	}
	
	public String toString() {
		StringWriter writer = new StringWriter();
		try {
			write(writer);
		} catch (IOException e) {
			// Should never happen
			throw new JsonException(e);
		}
		return writer.toString();
	}
	
	public void write(Writer writer) throws IOException {
		if(object != null) {
			format(writer, object);
			return;
		}
		if(map != null) {
			if(map.isEmpty()) {
				writer.write("{}");
				return;
			}
			writer.write("{");
			boolean first = true;
			for (Map.Entry<String, Json> entry : map.entrySet()) {
				if(!first) {
					writer.write(", ");
				} else {
					first = false;
				}
				formatString(writer, entry.getKey());
				writer.write(": ");
				entry.getValue().write(writer);
			}
			writer.write("}");
			return;
		}
		if(list != null) {
			if(list.isEmpty()) {
				writer.write("[]");
				return;
			}
			writer.write("[");
			boolean first = true;
			for (Json obj : list) {
				if(!first) {
					writer.write(", ");
				} else {
					first = false;
				}
				obj.write(writer);
			}
			writer.write("]");
			return;
		}
		writer.write("null");
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((list == null) ? 0 : list.hashCode());
		result = prime * result + ((map == null) ? 0 : map.hashCode());
		result = prime * result + ((object == null) ? 0 : object.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Json other = (Json) obj;
		if (list == null) {
			if (other.list != null)
				return false;
		} else if (!list.equals(other.list))
			return false;
		if (map == null) {
			if (other.map != null)
				return false;
		} else if (!map.equals(other.map))
			return false;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		return true;
	}
	
	public boolean equalsTo(Json other) {
		if(other == null) return false;
		if(this == other) return true;
		if(object != null) {
			if(isNumber()) {
				if(!other.isNumber()) return false;
				return Double.doubleToLongBits(asDouble()) == Double.doubleToLongBits(other.asDouble());
			}
			return object.equals(other.object);
		}
		if(list != null) {
			if(other.list == null) return false;
			int size = list.size();
			if(other.list.size() != size) return false;
			while(size-- > 0) {
				if(!list.get(size).equalsTo(other.list.get(size)))
					return false;
			}
		} else if(map != null) {
			if(other.map == null) return false;
			int size = map.size();
			if(other.map.size() != size) return false;
			for (String key : map.keySet()) {
				Json value = map.get(key);
				if(!value.equalsTo(other.map.get(key)))
					return false;
			}
		}
		return true;
	}
	
	public String str() {
		return asString();
	}
	
	public String asString() {
		if(object == null) return null;
		return object.toString();
	}
	
	public Date asDate() {
		if(object == null) return null;
		return (Date)object;
	}
	
	public boolean bool() {
		return asBoolean();
	}
	
	public boolean asBoolean() {
		if(object == null) return false;
		return ((Boolean) object).booleanValue();
	}
	
	public int asInt() {
		if(object == null) return 0;
		return ((Number) object).intValue();
	}
	
	public short asShort() {
		if(object == null) return 0;
		return ((Number) object).shortValue();
	}
	
	public byte asByte() {
		if(object == null) return 0;
		return ((Number) object).byteValue();
	}
	
	public long asLong() {
		if(object == null) return 0;
		return ((Number) object).longValue();
	}
	
	public Double asDouble() {
		if(object == null) return 0d;
		return ((Number) object).doubleValue();
	}
	
	public float asFloat() {
		if(object == null) return 0f;
		return ((Number) object).floatValue();
	}
	
	public boolean isNull() {
		return object == null && list == null && map == null;
	}
	
	public boolean isNumber() {
		return object != null && object instanceof Number;
	}
	
	public boolean isBoolean() {
		return object != null && object instanceof Boolean;
	}
	
	public boolean isString() {
		return object != null && object instanceof String;
	}

	public boolean isDate() {
		return object != null && object instanceof Date;
	}
	
	public boolean isMap() {
		return map != null;
	}
	
	public boolean isList() {
		return list != null;
	}
	
	public Json find(Object...params) {
		Json data = this;
		for (Object object : params) {
			if(object instanceof String && data.isMap()) {
				data = data.get((String) object);
				if(data == null) return null;
			} else if(object instanceof Integer && data.isList()) {
				int value = ((Integer) object).intValue();
				if(value >= data.size()) return null;
				data = data.at(value);
			} else {
				return null;
			}
		}
		return data;
	}
	
	public static Json loads(String s) {
		return new Parser(s).parse();
	}
	
	
	
} class Parser {
	
	private int n;
	private char[] s;
	private char c;
	private char[] buff;
	private int i;
	
	public Parser(String str) {
		this.s = str.toCharArray();
		buff = new char[s.length];
	}
	
	
	public Json parse() {
		c = s[n++];
		return next();
	}
	
	private Json next() {
		if(c == '\"') {
			i = 0;
			do {
				c = s[n++];
				if(c == '\\') {
					c = s[n++];
					switch(c) {
					case 'b':
						buff[i++] = '\b';
						continue;
					case 'f':
						buff[i++] = '\f';
						continue;
					case 'n':
						buff[i++] = '\n';
						continue;
					case 'r':
						buff[i++] = '\r';
						continue;
					case 't':
						buff[i++] = '\t';
						continue;
					case '\"':
					case '\\':
					case '/':
						buff[i++] = c;
						continue;
					case 'u':
						char u = (char) (Integer.parseInt(
								new String(s, n, 4), 16));
						n+=4;
						c = s[n];
						buff[i++] = u;
						continue;
					default:
						throw new JsonException("Invalid escape character: \\"+c+" at position "+n);
					}
				} else if(c == '\"') {
					c = s[n++];
					return new Json(new String(buff, 0, i));
				} // else
				buff[i++] = c;
			} while(true);
		} else if(c == '[') {
			Json result = Json.list();
			c = s[n++];
			while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
			if(c == ']') {
				if(n != s.length) c = s[n++];
				return result;
			}
			do {
				result.add(next());
				while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
				if(c == ']') {
					if(n != s.length) c = s[n++];
					return result;
				}
				if(c != ',') throw new JsonException("expected ',' or ']' at character "+n);
				c = s[n++];
				while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
			} while(true);
		} else if(c == '{') {
			Json result = Json.map();
			c = s[n++];
			while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
			if(c == '}') {
				if(n != s.length) c = s[n++];
				return result;
			}
			do {
				Json k = next();
				if(!k.isString())
					throw new JsonException("find non-string key at character "+n);
				String key = k.str();
				while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
				if(c != ':') throw new JsonException("expected ':' at character "+n);
				c = s[n++];
				while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
				Json value = next();
				result.put(key, value);
				while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
				if(c == '}') {
					if(n != s.length) c = s[n++];
					return result;
				}
				if(c != ',') throw new JsonException("expected: ',' or '}'' at character "+n);
				c = s[n++];
				while(c == ' ' || c == '\t' || c == '\n' || c == '\r') { c = s[n++]; }
			} while(true);
		} else if(c == 't') {
			if(s[n++] != 'r' || s[n++] != 'u' || s[n++] != 'e')
				throw new JsonException("expected 'true' at character "+n);
			if(n != s.length) c = s[n++];
			return new Json(Boolean.TRUE);
		} else if(c == 'f') {
			if(s[n++] != 'a' || s[n++] != 'l' || s[n++] != 's' || s[n++] != 'e')
				throw new JsonException("expected 'false' at character "+n);
			if(n != s.length) c = s[n++];
			return new Json(Boolean.FALSE);
		} else if(c == 'n') {
			if(s[n++] != 'u' || s[n++] != 'l' || s[n++] != 'l')
				throw new JsonException("expected 'true' at character "+n);
			if(n != s.length) c = s[n++];
			return new Json(null);
		} else if(c == '-' || (c >= '0' && c <= '9')) {
			i = 0;
			boolean d = false;
			do {
				buff[i++] = c;
				c = s[n++];
				if(c == '.' || c == 'e' || c == 'E') d = true;
			} while((c >= '0' && c <= '9') || c == 'e' ||
					c == 'E' || c == '.' || c == '+' || c == '-');
			if(d)
				return new Json(Double.parseDouble(new String(buff, 0, i)));
			return new Json(Long.parseLong(new String(buff, 0, i)));
		} else {
			throw new JsonException("expected: '{', '[', '\"', true, false, null, or a number at "+n);
		}
	}
	
	
}