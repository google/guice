package strategies.output;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.stream.Stream;

import benchmark.Config;

public class OutputToFile implements OutputStrategy {
	public OutputStream getOutputStream(Config config) {
		String file_full_path = config.getLocation();
		OutputStream outputStream = null;
		// checking if file exists
		File tempFile = new File(file_full_path);
		
		// source: https://www.w3schools.com/java/java_files_create.asp
		try {
			if (tempFile.createNewFile()) {
			    System.out.println("created file  " + tempFile.getName());
			} else {
			      System.out.println(tempFile.getName()+" already exists...");
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		
		// create output stream
		try {
			outputStream = new FileOutputStream(file_full_path);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		return outputStream;
	}
}
