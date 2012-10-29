package io.backbeam;

import java.util.Collections;
import java.util.List;

public class JoinResult {
	
	private int count;
	private List<BackbeamObject> results;
	
	public JoinResult(int count, List<BackbeamObject> results) {
		this.count = count;
		this.results = results;
	}
	
	public int getCount() {
		return count;
	}
	
	public List<BackbeamObject> getResults() {
		return Collections.unmodifiableList(results);
	}

}
