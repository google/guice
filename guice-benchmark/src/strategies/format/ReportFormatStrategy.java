package strategies.format;

import java.io.OutputStream;
import java.util.List;

import benchmark.StatsObject;

public interface ReportFormatStrategy {
	public void formatOutputStream (OutputStream stream, List<StatsObject> data);
}
