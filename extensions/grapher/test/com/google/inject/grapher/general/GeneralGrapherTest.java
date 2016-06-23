package com.google.inject.grapher.general;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.ShortNameFactory;
import com.google.inject.grapher.demo.BackToTheFutureModule;
import com.google.inject.grapher.general.test.SimpleInterface;
import com.google.inject.grapher.general.test.SimpleInterfaceProvider;
import com.google.inject.grapher.general.test.TestSimpleInterface;
import com.google.inject.grapher.graphviz.PortIdFactory;
import com.google.inject.grapher.graphviz.PortIdFactoryImpl;
import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * @author ksaric
 */
public class GeneralGrapherTest extends TestCase {

  private AbstractModule module;

  private NameFactory nameFactory;
  private PortIdFactory portIdFactory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.nameFactory = new ShortNameFactory();
    this.portIdFactory = new PortIdFactoryImpl();

    // the test module
    this.module = new BackToTheFutureModule();
  }

  public void testBackToTheFutureGraph() throws Exception {
    //Before
    final GeneralGrapher generalGrapher =
        new GeneralGrapher(nameFactory, portIdFactory) {
          @Override
          protected void postProcess() throws IOException {
            assertEquals(19, nodes.size());
            assertEquals(22, edges.size());
          }

          @Override
          protected void reset() {
            super.reset();

            assertEquals(0, nodes.size());
            assertEquals(0, edges.size());
          }
        };

    final Injector injector = Guice.createInjector(module);

    //When
    generalGrapher.graph(injector);
    generalGrapher.reset();
  }

  public void testSimpleBinding() throws Exception {
    //Before
    final GeneralGrapher generalGrapher =
        new GeneralGrapher(nameFactory, portIdFactory) {
          @Override
          protected void postProcess() throws IOException {
            assertEquals(2, nodes.size());
            assertEquals(1, edges.size());
          }
        };

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).to(TestSimpleInterface.class);
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    generalGrapher.graph(injector);
  }

  public void testSimpleProvider() throws Exception {
    //Before
    final GeneralGrapher generalGrapher =
        new GeneralGrapher(nameFactory, portIdFactory) {
          @Override
          protected void postProcess() throws IOException {
            assertEquals(2, nodes.size());
            assertEquals(1, edges.size());
          }
        };

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toProvider(SimpleInterfaceProvider.class);
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    generalGrapher.graph(injector);
  }

  public void testSimpleValue() throws Exception {
    //Before
    final GeneralGrapher generalGrapher =
        new GeneralGrapher(nameFactory, portIdFactory) {
          @Override
          protected void postProcess() throws IOException {
            assertEquals(2, nodes.size());
            assertEquals(1, edges.size());
          }
        };

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toInstance(new TestSimpleInterface());
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    generalGrapher.graph(injector);
  }

  public void testSimpleConstructor() throws Exception {
    //Before
    final GeneralGrapher generalGrapher =
        new GeneralGrapher(nameFactory, portIdFactory) {
          @Override
          protected void postProcess() throws IOException {
            assertEquals(1, nodes.size());
            assertEquals(0, edges.size());
          }
        };

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toConstructor(getConstructor());
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    generalGrapher.graph(injector);
  }

  /**
   * So we can hide exception handling.
   *
   * @return constructor
   */
  private Constructor<TestSimpleInterface> getConstructor() {
    try {
      return TestSimpleInterface.class.getConstructor();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return null;
  }
}
