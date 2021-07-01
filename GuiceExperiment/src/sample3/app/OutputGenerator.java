/**
 * 
 */
package sample3.app;

import java.io.OutputStream;
import java.util.List;

/**
 * @author polga
 *
 */
public class OutputGenerator {
	
	public void benchmarkOutput (Config config, List<StatsObject> data) {
		//pick appropriate according to config
		OutputStrategy outputStrategy = new OutputToScreen();
		outputStrategy = new OutputToFile();
		
		//pick appropriate according to config
		ReportFormatStrategy report = new CSVFormatter();
		report = new JSONFormatter();
		report = new HTMLFormatter();
	}

}
