/*CS304 Issue link:https://github.com/google/guice/issues/427
 */
package com.google.inject;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import junit.framework.TestCase;

import java.io.IOException;

public class BindingTest2 extends TestCase {

	public static class Foo {

		private final int bar;

		@Inject
		public Foo(@Named("foo") int bar) {
			this.bar = bar;
		}
	}

	public void setUp() throws IOException {
		Injector injector = Guice.createInjector(new AbstractModule() {
			@Override
			protected void configure() {
				bindConst().annotated(Names.named("foo"),Names.named("Foo")).to("Foo");
				expose(Key.get(Integer.class, Names.named("foo")));
				expose(Key.get(Integer.class, Names.named("Foo")));
				System.out.println(Foo.class);
			}

			private void expose(Key<Integer> foo) {
			}
		});
	}
	public void testAbstractModuleIsSerializable() throws IOException {
		Asserts.reserialize(new SerializationTest.MyAbstractModule());
	}


}
