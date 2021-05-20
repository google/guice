/**
 * 
 */
package benchmark;

/**
 * @author polga
 *
 */
public class TimingObj {
	private int startTime;
	private int endTime;
	private int className;
	
	/**
	 * 
	 * @param startTime
	 * @param className
	 */
	public TimingObj(int startTime, int className) {
		this.startTime = startTime;
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
	 * @return the endTime
	 */
	public int getEndTime() {
		return endTime;
	}
	/**
	 * @param endTime the endTime to set
	 */
	public void setEndTime(int endTime) {
		this.endTime = endTime;
	}
	/**
	 * @return the className
	 */
	public int getClassName() {
		return className;
	}
	/**
	 * @param className the className to set
	 */
	public void setClassName(int className) {
		this.className = className;
	}

	public void stopTiming() {
		// TODO Auto-generated method stub
		
	}
	
	

}
