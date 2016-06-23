package com.google.inject.grapher.xml;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.ShortNameFactory;
import com.google.inject.grapher.demo.BackToTheFutureModule;
import com.google.inject.grapher.general.test.DefaultSimpleInterface;
import com.google.inject.grapher.general.test.SimpleInterface;
import com.google.inject.grapher.general.test.SimpleInterfaceProvider;
import com.google.inject.grapher.graphviz.PortIdFactory;
import com.google.inject.grapher.graphviz.PortIdFactoryImpl;
import junit.framework.TestCase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;

/**
 * The xml data output overhead is low compared to general grapher, so we can simply
 * test the full output (compared to VisJs output).
 *
 * @author ksaric
 */
public class XmlGrapherTest extends TestCase {

  private XmlGrapher xmlGrapher;
  private StringWriter stringWriter;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    final NameFactory nameFactory = new ShortNameFactory();
    final PortIdFactory idFactory = new PortIdFactoryImpl();

    stringWriter = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(stringWriter);

    this.xmlGrapher = new XmlGrapher(nameFactory, idFactory);
    xmlGrapher.setOut(printWriter);
  }

  public void testSimpleBinding() throws Exception {
    //Before
    final String result =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<graph>\n"
            + "<node id=\"0\" name=\"SimpleInterface\" annotation=\"\" type=\"TYPE\"/>\n"
            + "<node id=\"1\" name=\"DefaultSimpleInterface\" annotation=\"\" type=\"INSTANCE\"/>\n"
            + "<edge head_id=\"0\" tail_id=\"1\" head_name=\"SimpleInterface\" tail_name=\"DefaultSimpleInterface\" type=\"BINDING\" binding_type=\"NORMAL\"/> \n"
            + "</graph>\n";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).to(DefaultSimpleInterface.class);
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    xmlGrapher.graph(injector);
    final String actual = stringWriter.toString();

    //Then
    assertEquals(result, actual);
  }

  public void testSimpleProvider() throws Exception {
    //Before
    final String result =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<graph>\n"
            + "<node id=\"0\" name=\"SimpleInterface\" annotation=\"\" type=\"TYPE\"/>\n"
            + "<node id=\"1\" name=\"SimpleInterfaceProvider\" annotation=\"\" type=\"INSTANCE\"/>\n"
            + "<edge head_id=\"0\" tail_id=\"1\" head_name=\"SimpleInterface\" tail_name=\"SimpleInterfaceProvider\" type=\"BINDING\" binding_type=\"PROVIDER\"/> \n"
            + "</graph>\n";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toProvider(SimpleInterfaceProvider.class);
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    xmlGrapher.graph(injector);
    final String actual = stringWriter.toString();

    //Then
    assertEquals(result, actual);
  }

  public void testSimpleValue() throws Exception {
    //Before
    final String result =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<graph>\n"
            + "<node id=\"0\" name=\"SimpleInterface\" annotation=\"\" type=\"TYPE\"/>\n"
            + "<node id=\"1\" name=\"SimpleInterface\" annotation=\"\" type=\"INSTANCE\"/>\n"
            + "<edge head_id=\"0\" tail_id=\"1\" head_name=\"SimpleInterface\" tail_name=\"SimpleInterface\" type=\"BINDING\" binding_type=\"NORMAL\"/> \n"
            + "</graph>\n";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toInstance(new DefaultSimpleInterface());
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    xmlGrapher.graph(injector);
    final String actual = stringWriter.toString();

    //Then
    assertEquals(result, actual);
  }

  public void testSimpleConstructor() throws Exception {
    //Before
    final String result =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<graph>\n"
            + "<node id=\"0\" name=\"SimpleInterface\" annotation=\"\" type=\"INSTANCE\"/>\n"
            + "</graph>\n";

    final AbstractModule testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SimpleInterface.class).toConstructor(getConstructor());
          }
        };
    final Injector injector = Guice.createInjector(testModule);

    //When
    xmlGrapher.graph(injector);
    final String actual = stringWriter.toString();

    //Then
    assertEquals(result, actual);
  }

  public void testXmlOutputBackToTheFuture() throws Exception {
    //Before
    final AbstractModule testModule = new BackToTheFutureModule();
    final Injector injector = Guice.createInjector(testModule);

    //When
    xmlGrapher.graph(injector);

    //Then
    assertEquals(19, xmlGrapher.getNodesContent().size());
    assertEquals(22, xmlGrapher.getEdgesContent().size());
  }

  /**
   * So we can hide exception handling.
   *
   * @return constructor
   */
  private Constructor<DefaultSimpleInterface> getConstructor() {
    try {
      return DefaultSimpleInterface.class.getConstructor();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }

    return null;
  }
}
