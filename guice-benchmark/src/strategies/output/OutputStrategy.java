package strategies.output;

import java.io.OutputStream;

public interface OutputStrategy {
	public OutputStream getOutputStream(Config config) ;
}
