package com.google.inject.grapher.xml;

import com.google.inject.AbstractModule;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.ShortNameFactory;
import com.google.inject.grapher.graphviz.PortIdFactory;
import com.google.inject.grapher.graphviz.PortIdFactoryImpl;

/**
 * Module that provides classes needed by {@link XmlGrapher}.
 *
 * @author ksaric
 */
public class XmlModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(NameFactory.class).annotatedWith(Xml.class).to(ShortNameFactory.class);
    bind(PortIdFactory.class).annotatedWith(Xml.class).to(PortIdFactoryImpl.class);
  }
}
