/**
 * 
 */
package sample2.app;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Blurb goes here
 * 
 * @author Maré Sieling
 *
 */
public class CalculatorClient {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Injector injector = Guice.createInjector(new CalculatorModule2());
		Calculator calculator = injector.getInstance(Calculator.class);
		System.out.println(calculator);
		System.out.println("Addition: " + calculator.add(50, 100));
		System.out.println("Subtraction: " + calculator.subtract(200, 100));
		System.out.println("Multiplication: " + calculator.multiply(50, 12));
		System.out.println("Division: " + calculator.divide(90, 20));
		Calculator calculator2 = injector.getInstance(Calculator.class);
		System.out.println(calculator2);
	}
}
