/**
 * 
 */
package strategies;

import java.io.IOException;
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
 * Developed as class project for CSS553 at University of Washington (Bothell)
 * 
 * @author Gucci Team
 */
public class OutputGenerator {
	private Map<String, ReportFormatStrategy> formatLookup;
	private Map<String, OutputStrategy> outputLookup;
	private static OutputGenerator outputGen = null;

	/**
	 * Constructor loads formats for lookup tables
	 */
	private OutputGenerator() {
		formatLookup = new HashMap<>();
		formatLookup.put("json", new JSONFormatter());
		formatLookup.put("html", new HTMLFormatter());
		formatLookup.put("csv", new CSVFormatter());

		outputLookup = new HashMap<>();
		outputLookup.put("screen", new OutputToScreen());
		outputLookup.put("file", new OutputToFile());
	}

	/**
	 * Returns instance of OutputGenerator
	 * 
	 * @return
	 */
	public static synchronized OutputGenerator getInstance() {
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
	public void benchmarkOutput(Config config, List<StatsObject> data) {
		// TODO add error checking
		if (config == null){
			throw new RuntimeException("Output error: Configuration is null");
		}
		
		if (config.getOutputType() == null ) {
			throw new RuntimeException("Output error: no output type set");
		}

		if (data == null || data.size()== 0) {
			throw new RuntimeException("Output error: No data to output");
		}

		OutputStrategy outputStrategy = outputLookup.get(config.getOutputType());
		if (outputStrategy == null) {
			throw new RuntimeException("Output error: outputstream strategy not created: '" + config.getOutputType() + "'");
		}
		
		ReportFormatStrategy report = formatLookup.get(config.getOutputFormat());
		if (report == null) {
			throw new RuntimeException("Output error: report strategy not created: '" + config.getOutputFormat() + "'");
		}
		
		try {
			OutputStream ostream = outputStrategy.getOutputStream(config);
			report.formatOutputStream(ostream, data);
			ostream.flush();
		} catch (IOException e) {
			throw new RuntimeException("Outputstream error", e);
		}
		finally
		{
			outputStrategy.close();
		}
	}

}
