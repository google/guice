package strategies.format;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
//import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import benchmark.StatsObject;
//import java.lang.Object
//import com.oracle.json.Json;

public class JSONFormatter implements ReportFormatStrategy {

	@Override
	public void formatOutputStream(OutputStream stream, List<StatsObject> data) {
		// TODO Auto-generated method stub
		// source: https://mkyong.com/java/how-to-enable-pretty-print-json-output-gson/
		Gson gson = new Gson();
		Map<Integer,Map<String,Integer>> stats_metrics_json = new HashMap<Integer, Map<String,Integer>>();
		
		int count = 0;
		for (StatsObject d:data) {
			Map<String,Integer> stats_metrics = new HashMap<String,Integer>();
			stats_metrics.put("Average", d.getAverage());
			stats_metrics.put("Max", d.getMax());
			stats_metrics.put("Min", d.getMin());
			stats_metrics.put("P50", d.getP50());
			stats_metrics.put("P90", d.getP90());
			stats_metrics.put("P99", d.getP99());
			stats_metrics_json.put(count, stats_metrics);
			count += 1;
		}
		

        String json = gson.toJson(stats_metrics_json);
		try {
			// source: https://stackoverflow.com/a/21974655/11912703
			OutputStreamWriter osw = new OutputStreamWriter(stream, "UTF-8");
			osw.write(json.toString());
			osw.flush();
			osw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
