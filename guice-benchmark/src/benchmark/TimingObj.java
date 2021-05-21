/**
 * 
 */
package benchmark;

/**
 *
 */
public class TimingObj {
	private long startTime;
	private long endTime;
	private String className;

	/**
	 * 
	 * @param startTime
	 * @param className
	 */
	public TimingObj(long startTime, String className) {
		this.startTime = startTime;
		this.className = className;
	}

	/**
	 * @return the className
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * 
	 */
	public void stopTiming() {
		this.endTime = System.currentTimeMillis();
	}

	/**
	 * 
	 * @return
	 */
	public long duration() {
		return endTime - startTime;
	}

}
