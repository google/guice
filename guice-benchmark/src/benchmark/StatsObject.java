package benchmark;
/**
 * 
 */

/**
 * @author polga
 *
 */
public class StatsObject {

	private int startTime;
	private int stopTime;
	private int average;
	private String className;

	public StatsObject(int startTime, int stopTime, int average, String className) {
		this.startTime = startTime;
		this.stopTime = stopTime;   //not needed at start
		this.average = average;     //not needed at start
		this.className = className;
	}

	/**
	 * @return the startTime
	 */
	public int getStartTime() {
		return startTime;
	}

	/**
	 * @param startTime the startTime to set
	 */
	public void setStartTime(int startTime) {
		this.startTime = startTime;
	}

	/**
	 * @return the stopTime
	 */
	public int getStopTime() {
		return stopTime;
	}

	/**
	 * @param stopTime the stopTime to set
	 */
	public void setStopTime(int stopTime) {
		this.stopTime = stopTime;
	}

	/**
	 * @return the average
	 */
	public int getAverage() {
		return average;
	}

	/**
	 * @param average the average to set
	 */
	public void setAverage(int average) {
		this.average = average;
	}

	public void stopTiming() {

	}
}
