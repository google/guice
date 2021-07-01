package sample3.app;

import java.io.OutputStream;
import java.util.List;

public interface ReportFormatStrategy {
	public void formatOutputStream (OutputStream stream, List<StatsObject> data);
}
