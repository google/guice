package strategies.output;

import java.io.OutputStream;
import java.util.stream.Stream;

import benchmark.Config;

public class OutputToScreen implements OutputStrategy{
	
	public OutputStream getOutputStream(Config config) {
		OutputStream outputStream = System.out;
		return outputStream;
	}

}
