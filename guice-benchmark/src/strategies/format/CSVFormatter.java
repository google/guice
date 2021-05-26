package strategies.format;

import java.io.OutputStream;
import java.util.List;
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

		stream.write("Class Name, Average, Max, Min, P50, P90, P99");
		for(int i=0; i< data.size(); i++) {

			String temp=data.get(i).getClassName()+","+data.get(i).getAverage()+","+data.get(i).getMax()
					+","+data.get(i).getMin()+","+data.get(i).getP50()+","+data.get(i).getP90()+","+data.get(i).getP99()+"\n";
			stream.write(temp);

		}

		}


}
