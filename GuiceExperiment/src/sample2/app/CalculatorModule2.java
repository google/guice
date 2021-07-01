/**
 * 
 */
package sample2.app;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

/**
 * @author polga
 *
 */
public class CalculatorModule2 extends AbstractModule{

	@Override
	protected void configure() {
		//bind (Calculator.class).to(CalculatorImpl.class).in(Singleton.class);
		//bind (Calculator.class).to(CalculatorImpl.class).in(Scopes.SINGLETON);
		
		bind (Calculator.class).toInstance(new CalculatorImpl());
	}
	
}
