/**
 * 
 */
package sample3.app;

import com.google.inject.Singleton;

/**
 * @author polga
 *
 */
@Singleton
public class CalculatorImpl implements Calculator{

	@Override
	public int add(int a, int b) {
		return a + b;
	}

	@Override
	public int subtract(int a, int b) {
		return a - b;
	}

	@Override
	public int multiply (int a, int b) {
		return a * b;
	}

	@Override
	public double  divide (int a, int b) {
		return a / b;
	}
}