package benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 */

/**
 * @author Luyao
 *
 */
public class StatsObject {

	private List<Long> timings; // needs to be sorted at some point before stats is given
	private String className;
	private long max;
	private long min;
	private long sum;

	/**
	 * 
	 * @param className
	 */
	public StatsObject(String className) {
		this.timings = new ArrayList<Long>();
		this.className = className;
	}
	
	/**
	 * 
	 * @return className of the class whose injected durations are managed by this StatsObject
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * 
	 * @param time
	 */
	public void addTiming(long time) {
		// add the incoming new time into the list
		timings.add(time);
		
		// update the max and min if necessary
		if(time < min)
			min = time;
		if(time > max)
			max = time;
		
		// update sum
		sum += time;
	}

	/**
	 * @return the average
	 */
	public long getAverage() {
		// use sum to calculate average
		long average = 0;
		if(!timings.isEmpty())
			average = sum / timings.size();
		
		return average;
	}

	/**
	 * Gives the worst time
	 * @return the max duration in the timings list
	 */
	public long getMax() {
		return max;
	}

	/**
	 * Gives the best time
	 * @return the min duration in the timing list
	 */
	public long getMin() {
		return min;
	}

	// give other stats?
}
