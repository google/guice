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
 * @author polga
 *
 */
public class OutputGenerator {
	Map <String, ReportFormatStrategy> formatLookup;
	
	public void benchmarkOutput (Config config, List<StatsObject> data) {
		//pick appropriate according to config
		OutputStrategy outputStrategy = new OutputToScreen();
		outputStrategy = new OutputToFile();
		OutputStream ostream = outputStrategy.getOutputStream();
		
		//pick appropriate according to config
		ReportFormatStrategy report = new CSVFormatter();
		report = new JSONFormatter();
		report = new HTMLFormatter();
		
		formatLookup = new HashMap<>();
		formatLookup.put ("json", new JSONFormatter());
		formatLookup.put ("html", new HTMLFormatter());
		formatLookup.put ("csv", new CSVFormatter());
		
		report = formatLookup.get(config.getOutputFormat());
		report.formatOutputStream(ostream, data);
		
		
	}

}
