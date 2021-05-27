package strategies.output;

import java.io.OutputStream;
import benchmark.Config;

/**
 * 
 * Developed as class project for CSS553 at University of Washington (Bothell)
 * 
 * @author Gucci Team
 *
 */
public interface OutputStrategy {

	public OutputStream getOutputStream(Config config);
	public void close();
}
