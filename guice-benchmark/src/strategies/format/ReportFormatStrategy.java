package strategies.format;

import java.io.OutputStream;
import java.util.List;

import benchmark.StatsObject;

/**
 * Report formatting strategy interface
 * 
 * Developed as class project for CSS553 at University of Washington (Bothell)
 * 
 * @author Gucci Team
 *
 */
public interface ReportFormatStrategy {
	public void formatOutputStream(OutputStream stream, List<StatsObject> data);
}
