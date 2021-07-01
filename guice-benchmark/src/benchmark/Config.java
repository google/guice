package benchmark;

/**
 * Configuration Developed as class project for CSS553 at University of
 * Washington (Bothell)
 * 
 * @author Gucci Team *
 */
public class Config {
	private String outputFormat;
	private String outputType; // change name to outputType
	private String filename;

	public Config() {
		// assigns everything
		this.outputFormat = "csv";
		this.outputType = "screen";
	}

	public Config(String outputFormat) {
		this.outputFormat = outputFormat;
		this.outputType = "screen";
	}

	public Config(String outputFormat, String location) {
		this.outputFormat = outputFormat;
		this.outputType = location;
	}
	
	public Config(String outputFormat, String outputType, String filename) {
		this.outputFormat = outputFormat;
		this.outputType = outputType;
		this.filename = filename;
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
	 * Name change to outputType
	 * 
	 * @return the location
	 */
	@Deprecated
	public String getLocation() {
		return outputType;
	}

	/**
	 * Name change to outputType
	 * 
	 * @param location the location to set
	 */
	@Deprecated
	public void setLocation(String location) {
		this.outputType = location;
	}

	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}

	/**
	 * @return the outputType
	 */
	public String getOutputType() {
		return outputType;
	}

	/**
	 * @param outputType the outputType to set
	 */
	public void setOutputType(String outputType) {
		this.outputType = outputType;
	}

}
