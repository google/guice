package benchmark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 *
 */
public class StatsManager {
	
	//key should be reference to instantiated / injected object: Name of object (class)
	private Map<String,StatsObject> data;
	private TimingObj timer;
	
	/**
	 * Starts timing 
	 * @param <T>
	 * @param type
	 * @return
	 */
	public <T> TimingObj startTiming(Class<T> type) {
		timer = new TimingObj(System.currentTimeMillis(),type.getTypeName());
		return timer;
	}

	/**
	 * Converts map of data to list
	 * 
	 * @return
	 */
	public List<StatsObject> getData (){
		List<String> list = new ArrayList<String>(data.values());
		return list;		
	}

	/**
	 * Update data with new object info
	 * 
	 * @param timingObj
	 */
	public void updateData(TimingObj timingObj) {
		timer.className = timingObj.className;
		timer.startTime = timingObj.startTime;
		timer.endTime = timingObj.endTime;
	}
}
