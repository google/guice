package sample3.app;

import com.google.inject.Inject;

public class CalculatorService {
	
	@Inject
	private Calculator calculator;
	
	/**
	 * Calculates according to oper
	 * 
	 * @param a
	 * @param b
	 * @param oper
	 * @return calculated value
	 */
	public double calculateMe (int a, int b, String oper) {
		if (oper.equals("add")) {
			return (double) calculator.add(a, b);
		}
		else if (oper.equals("subtract")) {
			return (double) calculator.subtract(a, b);
		}
		else if (oper.equals("multiply")) {
			return (double) calculator.multiply(a, b);
		}
		else if (oper.equals("divide")) {
			return calculator.divide(a, b);
		}
		return 0;
	}

}
