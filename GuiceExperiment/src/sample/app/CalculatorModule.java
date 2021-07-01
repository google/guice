/**
 * 
 */
package sample.app;

import com.google.inject.AbstractModule;

/**
 * @author polga
 *
 */
public class CalculatorModule extends AbstractModule{

	@Override
	protected void configure() {
		bind (Calculator.class);
	}
	
}
