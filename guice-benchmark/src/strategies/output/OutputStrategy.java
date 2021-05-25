package strategies.output;

import java.io.OutputStream;
import benchmark.Config;

public interface OutputStrategy {
	public OutputStream getOutputStream(Config config) ;
}
