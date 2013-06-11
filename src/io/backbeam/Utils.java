package io.backbeam;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

public class Utils {
	
	public static String urlEncode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String reader2String(Reader reader) throws IOException {
        try {
            StringWriter writer = new StringWriter();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
            return writer.toString();
        } finally {
            reader.close();
        }
    }
	
	public static String hmacSha1(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secret = new SecretKeySpec(key.getBytes("UTF-8"), mac.getAlgorithm());
            mac.init(secret);
            byte[] digest = mac.doFinal(message.getBytes("UTF-8"));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new BackbeamException(e);
        }
	}
	
	public static String hexString(byte[] b) {
		StringBuilder result = new StringBuilder();
		for (int i=0; i<b.length; i++) {
			result.append(Integer.toString(( b[i] & 0xff) + 0x100, 16).substring(1));
		}
		return result.toString();
	}
	
	public static byte[] sha1(byte[] input) {
	    MessageDigest md = null;
	    try {
	        md = MessageDigest.getInstance("SHA-1");
	    } catch(NoSuchAlgorithmException e) {
	        throw new RuntimeException(e); // should never happen
	    } 
	    return md.digest(input);
	}
	
	public static List<String> stringsFromParams(Object[] params) {
		if (params == null) return Collections.emptyList();
		List<String> list = new ArrayList<String>(params.length);
		for (Object obj : params) {
			list.add(stringFromObject(obj, true));
		}
		return list;
	}
	
	public static String stringFromObject(Object obj, boolean addEntity) {
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

}
