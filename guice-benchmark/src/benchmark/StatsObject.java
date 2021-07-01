package benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 
 */

/**
 * Developed as class project for CSS553 at University of Washington (Bothell)
 * 
 * @author Gucci Team
 *
 */
public class StatsObject {

	private List<Long> timings; // needs to be sorted at some point before stats is given
	private String className;
	private long max = Long.MIN_VALUE;
	private long min = Long.MAX_VALUE;
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

	/**
	 * Gives the 50 percentile - median
	 * @return the p50 duration in the timing list
	 */
	public long getP50() {
		long result = 0L;
		
		if(!timings.isEmpty())
		{
			timings.sort(Comparator.naturalOrder());
			int index = (int) (timings.size() * 0.5);
			result = timings.get(index);
		}
		return result;
	}
	
	/**
	 * Gives the 90 percentile - median
	 * @return the p90 duration in the timing list
	 */
	public long getP90() {
		long result = 0L;
		
		if(!timings.isEmpty())
		{
			timings.sort(Comparator.naturalOrder());
			int index = (int) (timings.size() * 0.9);
			result = timings.get(index);
		}
		return result;
	}
	
	/**
	 * Gives the 99 percentile - median
	 * @return the p99 duration in the timing list
	 */
	public long getP99() {
		long result = 0L;
		
		if(!timings.isEmpty())
		{
			timings.sort(Comparator.naturalOrder());
			int index = (int) (timings.size() * 0.99);
			result = timings.get(index);
		}
		return result;
	}
}
