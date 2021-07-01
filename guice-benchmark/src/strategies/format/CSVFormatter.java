package strategies.format;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import benchmark.StatsObject;
import java.io.IOException;

/**
 * 
 * @author Gucci
 *
 */

public class CSVFormatter implements ReportFormatStrategy {

	@Override
	public void formatOutputStream(OutputStream stream, List<StatsObject> data) {

		try {
			stream.write("Class Name, Average, Max, Min, P50, P90, P99\n".getBytes(StandardCharsets.UTF_8));
			for (int i = 0; i < data.size(); i++) {

				String temp = data.get(i).getClassName() + "," + data.get(i).getAverage() + "," + data.get(i).getMax()
						+ "," + data.get(i).getMin() + "," + data.get(i).getP50() + "," + data.get(i).getP90() + ","
						+ data.get(i).getP99() + "\n";
				stream.write(temp.getBytes(StandardCharsets.UTF_8));

			}
		} catch (IOException e) {
			throw new RuntimeException("Error formatting record to CSV", e);
		}

	}

}
