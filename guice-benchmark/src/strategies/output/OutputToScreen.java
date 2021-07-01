package strategies.output;

import java.io.OutputStream;

import benchmark.Config;

public class OutputToScreen implements OutputStrategy{
	
	public OutputStream getOutputStream(Config config) {
		OutputStream outputStream = System.out;
		return outputStream;
	}

	@Override
	public void close() {
		// We do NOT want to close System.out
	}

}
