/*CS304 Issue link:https://github.com/google/guice/issues/1082
 */
package com.google.inject;
import com.google.common.collect.ImmutableList;
import com.google.inject.spi.Message;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.IOException;
import java.util.List;

public class skTest extends TestCase {

	static class A{
		public void method1(){
			System.out.println("old");
		}
		public void method2(){
			System.out.println("old");
		}
		public void method3(){
			System.out.println("old");
		}
	}
	static abstract class B extends A {

	}

	static class BB extends B{
		@Override
		public void method1(){
			System.out.println("new");
		}
		public void method2(){
			System.out.println("new");
		}
		public void method3(){
			System.out.println("new");
		}
	}

	private static Injector injector;
	public void setUp() throws IOException {
		injector = Guice.createInjector(new AbstractModule() {
			@Override
			protected void configure() {
				convertBind(A.class,BB.class).convertTo(BB.class);
				skTest.BB bb=new skTest.BB();
				bb.method1();
			}
		});
	}
	public void testAbstractModuleIsSerializable() throws IOException {
		Asserts.reserialize(new SerializationTest.MyAbstractModule());
	}
}



