package com.google.inject.grapher.visjs;

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
import org.easymock.internal.ReflectionUtils;
import org.junit.Assert;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;

/**
 * @author ksaric
 */
public class VisJsGrapherTest extends TestCase {

  private VisJsGrapher grapher;
  private StringWriter stringWriter;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    final NameFactory nameFactory = new ShortNameFactory();
    final PortIdFactory idFactory = new PortIdFactoryImpl();

    stringWriter = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(stringWriter);

    grapher = new VisJsGrapher(nameFactory, idFactory);
    grapher.setOut(printWriter);
  }

  public void testSimpleBinding() throws Exception {
    //Before
    final String node1 = "\t{id: 0, label: 'SimpleInterface', color:'#7BE141'},";
    final String node2 = "\t{id: 1, label: 'TestSimpleInterface', color:'#6E6EFD'},";

    final String edge1 = "\t{from: 0, to: 1, color:'#97C2FC', arrows:'to' },";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).to(TestSimpleInterface.class);
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    grapher.graph(injector);

    //Then
    Assert.assertEquals(2, grapher.getNodesContent().size());
    Assert.assertEquals(node1, grapher.getNodesContent().get(0));
    Assert.assertEquals(node2, grapher.getNodesContent().get(1));

    Assert.assertEquals(1, grapher.getEdgesContent().size());
    Assert.assertEquals(edge1, grapher.getEdgesContent().get(0));
  }

  public void testSimpleProvider() throws Exception {
    //Before
    final String node1 = "\t{id: 0, label: 'SimpleInterface', color:'#7BE141'},";
    final String node2 = "\t{id: 1, label: 'SimpleInterfaceProvider', color:'#6E6EFD'},";

    final String edge1 = "\t{from: 0, to: 1, color:'#FFA807', arrows:'to' },";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toProvider(SimpleInterfaceProvider.class);
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    grapher.graph(injector);

    //Then
    Assert.assertEquals(2, grapher.getNodesContent().size());
    Assert.assertEquals(node1, grapher.getNodesContent().get(0));
    Assert.assertEquals(node2, grapher.getNodesContent().get(1));

    Assert.assertEquals(1, grapher.getEdgesContent().size());
    Assert.assertEquals(edge1, grapher.getEdgesContent().get(0));
  }

  public void testSimpleValue() throws Exception {
    //Before
    final String node1 = "\t{id: 0, label: 'SimpleInterface', color:'#7BE141'},";
    final String node2 = "\t{id: 1, label: 'SimpleInterface', color:'#6E6EFD'},";

    final String edge1 = "\t{from: 0, to: 1, color:'#97C2FC', arrows:'to' },";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toInstance(new TestSimpleInterface());
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    grapher.graph(injector);

    //Then
    Assert.assertEquals(2, grapher.getNodesContent().size());
    Assert.assertEquals(node1, grapher.getNodesContent().get(0));
    Assert.assertEquals(node2, grapher.getNodesContent().get(1));

    Assert.assertEquals(1, grapher.getEdgesContent().size());
    Assert.assertEquals(edge1, grapher.getEdgesContent().get(0));
  }

  public void testSimpleConstructor() throws Exception {
    //Before
    final String node1 = "\t{id: 0, label: 'SimpleInterface', color:'#6E6EFD'},";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toConstructor(getConstructor());
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    grapher.graph(injector);

    //Then
    Assert.assertEquals(1, grapher.getNodesContent().size());
    Assert.assertEquals(node1, grapher.getNodesContent().get(0));

    Assert.assertEquals(0, grapher.getEdgesContent().size());
  }

  public void testVizJsOutput() throws Exception {
    //Before
    final AbstractModule testModule = new BackToTheFutureModule();
    final Injector injector = Guice.createInjector(testModule);

    //When
    grapher.graph(injector);
    final String actual = stringWriter.toString();

    //Then
    Assert.assertNotNull(actual);
  }

  /**
   * So we can hide exception handling.
   *
   * @return constructor
   */
  private Constructor<TestSimpleInterface> getConstructor() {
    try {
      return ReflectionUtils.getConstructor(TestSimpleInterface.class);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return null;
  }
}
