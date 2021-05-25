package strategies.format;

import java.io.OutputStream;
import java.util.List;

import benchmark.StatsObject;

/**
 * 
 * @author Meghna
 *
 */
public class HTMLFormatter implements ReportFormatStrategy {

	@Override
	public void formatOutputStream(OutputStream stream, List<StatsObject> data) {
		// TODO Auto-generated method stub
		OutputStream clientOutput = client.getOutputStream();

		// HTML headers
		clientOutput.write("HTTP/1.1 200 OK\r\n".getBytes());
		clientOutput.write(("ContentType: text/html\r\n").getBytes());
		clientOutput.write("\r\n".getBytes());

		Scanner scanner = new Scanner(new File("htmlformat.html"));
		String htmlString = scanner.useDelimiter("\\Z").next();
		scanner.close();
		clientOutput.write(htmlString.getBytes("UTF-8"));

		clientOutput.write("\r\n\r\n".getBytes());
		clientOutput.flush();


	}

}
