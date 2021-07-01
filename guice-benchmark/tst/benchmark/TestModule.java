package benchmark;

import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ExpensiveSingleton.class);
		bind(RandomObject.class);
		bind(SimpleClass.class);
	}
}
