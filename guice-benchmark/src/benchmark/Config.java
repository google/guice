package benchmark;

public class Config {
	private String outputFormat;
	private String location;
	
	public Config() {
		//assigns everything
		this.outputFormat = "default";
		this.location = "default";
	}
	
	public Config (String outputFormat) {
		this.outputFormat = outputFormat;
		this.location = "default";
	}
	
	public Config (String outputFormat, String location) {
		this.outputFormat = outputFormat;
		this.location = location;
	}

	/**
	 * @return the outputFormat
	 */
	public String getOutputFormat() {
		return outputFormat;
	}

	/**
	 * @param outputFormat the outputFormat to set
	 */
	public void setOutputFormat(String outputFormat) {
		this.outputFormat = outputFormat;
	}

	/**
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * @param location the location to set
	 */
	public void setLocation(String location) {
		this.location = location;
	}
}
