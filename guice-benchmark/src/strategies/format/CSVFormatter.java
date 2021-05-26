package strategies.format;

import java.io.OutputStream;
import java.util.List;
import com.opencsv.CSVWriter;
import benchmark.StatsObject;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;


/**
 * 
 * @author Gucci
 *
 */

public class CSVFormatter implements ReportFormatStrategy{

	@Override
	public void formatOutputStream(OutputStream stream, List<StatsObject> data) {


		List<String[]> csvData = createCsvDataSimple();

		// default all fields are enclosed in double quotes
		// default separator is a comma
		try ()
		{
			CSVWriter writer = new CSVWriter(new FileWriter("c:\\benchmark.csv"));
			writer.writeAll(csvData);
			writer.close();
		}


		private static List<String[]> createCsvDataSimple() {
			String[] header = {"Average", "Max", "Min", "P50", "P90", "P99"};
			List<String[]> list = new ArrayList<>();
			list.add(header);

			//loop through List<StatsObject> to store information in an array format as below

			//like String[] record1 = {"1", "first name", "address 1", "11111"};
			//like String[] record2 = {"2", "second name", "address 2", "22222"};
			//list.add(each array)

			//like list.add(record1);

			return list;

			//Source: https://mkyong.com/java/how-to-export-data-to-csv-file-java/
			//additional: https://stackoverflow.com/questions/35749250/java-write-the-list-string-into-csv-file
		}

	}
}
