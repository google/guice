package benchmark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * Developed as class project for CSS553 at University of Washington (Bothell)
 * 
 * @author Gucci Team *
 */
public class StatsManager {

	// key should be reference to instantiated / injected object: Name of object
	// (class)
	private Map<String, StatsObject> data;

	/**
	 * Starts timing
	 * 
	 * @param <T>
	 * @param type
	 * @return
	 */
	public <T> TimingObj startTiming(Class<T> type) {
		TimingObj timingObj = new TimingObj(System.currentTimeMillis(), type.getTypeName());
		return timingObj;
	}

	/**
	 * Converts map of data to list 
	 * 
	 * Jason
	 * 
	 * @return
	 */
	public List<StatsObject> getData() {
		// convert map to list
		// return list
		return null;
	}

	/**
	 * Update data with new object info 
	 * 
	 * Jason
	 * 
	 * @param timingObj
	 */
	public void updateData(TimingObj timingObj) {
		// TODO
		// find the statsobj in the data map with the key from the timingObj
		// send the new time to the statsobj
	}
}
