package benchmark;

import java.util.List;
import java.util.Map;

public class StatsManager {
	
	//key should be reference to instantiated / injected object
	private Map<String,StatsObject> data;
	
	public <T> TimingObj startTiming(Class<T> type) {
		return null;
	}
	
	
	
	public List<StatsObject> getData (){
		//convert map to list
		return null;		
	}

	
	
	//calculate average - TODO
	
	//calculate p50 (50th percentile)
	
	//calculate p90
	
	//calculate p99
	
}
