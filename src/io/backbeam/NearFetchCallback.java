package io.backbeam;

import java.util.List;

public abstract class NearFetchCallback extends Callback {
	
	public abstract void success(List<BackbeamObject> objects, int totalCount, List<Integer> distances, boolean fromCache);

}
