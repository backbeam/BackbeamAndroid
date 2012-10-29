package io.backbeam;

import java.util.List;

public abstract class FetchCallback extends Callback {
	
	public abstract void success(List<BackbeamObject> objects);

}
