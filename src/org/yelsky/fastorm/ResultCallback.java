package org.yelsky.fastorm;

import java.util.List;

public interface ResultCallback {
	public void onResult(List data,int call_id);
}
