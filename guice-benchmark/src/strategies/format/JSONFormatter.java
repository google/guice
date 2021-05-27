package strategies.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import benchmark.StatsObject;

/**
 * Developed as class project for CSS553 at University of Washington (Bothell)
 * 
 * @author Gucci Team*
 *
 */
public class JSONFormatter implements ReportFormatStrategy {

	@Override
	public void formatOutputStream(OutputStream stream, List<StatsObject> data) {
		// source: https://mkyong.com/java/how-to-enable-pretty-print-json-output-gson/
		Gson gson = new Gson();
		Map<String, Map<String, Long>> stats_metrics_json = new HashMap<String, Map<String, Long>>();

		for (StatsObject d : data) {
			Map<String, Long> stats_metrics = new HashMap<>();
			stats_metrics.put("Average", d.getAverage());
			stats_metrics.put("Max", d.getMax());
			stats_metrics.put("Min", d.getMin());
			stats_metrics.put("P50", d.getP50());
			stats_metrics.put("P90", d.getP90());
			stats_metrics.put("P99", d.getP99());
			stats_metrics_json.put(d.getClassName(), stats_metrics);
		}
		String json = gson.toJson(stats_metrics_json);
		try {
			stream.write(json.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException("Error formatting record to JSON", e);
		}
	}
}
