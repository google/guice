/**
 * 
 */
package strategies;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import benchmark.Config;
import benchmark.StatsObject;
import strategies.format.CSVFormatter;
import strategies.format.HTMLFormatter;
import strategies.format.JSONFormatter;
import strategies.format.ReportFormatStrategy;
import strategies.output.OutputStrategy;
import strategies.output.OutputToFile;
import strategies.output.OutputToScreen;


/**
 * Implemented as singleton
 *
 */
public class OutputGenerator {
	private Map <String, ReportFormatStrategy> formatLookup;
	//Map for outputstream
	private static OutputGenerator outputGen = null;
	
	/**
	 * 
	 */
	private OutputGenerator() {
		formatLookup = new HashMap<>();
		formatLookup.put ("json", new JSONFormatter());
		formatLookup.put ("html", new HTMLFormatter());
		formatLookup.put ("csv", new CSVFormatter());
		
		//create similar for output stream
	}
	
	/**
	 * Returns instance of OutputGenerator
	 * @return
	 */
	public static OutputGenerator getInstance () {
		if (outputGen == null) {
			outputGen = new OutputGenerator();
		}
		return outputGen;
	}
	
	/**
	 * Outputs the benchmark data
	 * 
	 * @param config
	 * @param data
	 */
	public void benchmarkOutput (Config config, List<StatsObject> data) {

		//pick appropriate according to config
		//OutputStrategy outputStrategy;
		//OutputStream ostream = outputStrategy.getOutputStream(config);
		
		//pick appropriate according to config
		//ReportFormatStrategy report ;	
		//report = formatLookup.get(config.getOutputFormat());
		//report.formatOutputStream(ostream, data);
		
		//TODO
		//delete above
		//get the correct stream (screen or file)
		//format the stream
		//output the stream
	}
}
