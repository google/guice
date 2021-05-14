/**
 * 
 */
package sample3.app;

import com.google.inject.Guice;
import sample3.app.BenchmarkInjector;
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
	public static void main(String[] args) 
	{
		BenchmarkInjector injector =  BenchmarkInjector.createInjector(new CalculatorModule2());
		CalculatorService calculatorService = injector.getInstance(CalculatorService.class);

		System.out.println("Addition: " + calculatorService.calculateMe(50, 100, "add"));
		System.out.println("Subtraction: " + calculatorService.calculateMe(200, 100, "subtract"));
		System.out.println("Multiplication: " + calculatorService.calculateMe(50, 12, "multiply"));
		System.out.println("Division: " + calculatorService.calculateMe(90, 20, "divide"));
		


	}
}
