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

	/**
	 * 
	 * @param className
	 */
	public StatsObject(String className) {
		this.timings = new ArrayList<>();
		this.className = className;
	}

		/**
	 * 
	 * @return
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * 
	 * @param time
	 */
	public void addTiming(long time) {
		// add time
		// sort list
	}

	/**
	 * @return the average
	 */
	public long getAverage() {
		return 0;
	}

	// give worst time

	// give best time

	// give other stats?
}
