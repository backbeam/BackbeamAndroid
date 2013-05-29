package io.backbeam;

import java.util.Map;

public interface RealTimeEventListener {
	
	public void realTimeEventReceived(String name, Map<String, String> message);

}
