package com.google.inject.grapher.visjs;

import com.google.inject.AbstractModule;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.ShortNameFactory;
import com.google.inject.grapher.graphviz.PortIdFactory;
import com.google.inject.grapher.graphviz.PortIdFactoryImpl;

/**
 * Module that provides classes needed by {@link VisJsModule}.
 *
 * @author ksaric
 */
public class VisJsModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(NameFactory.class).annotatedWith(VisJs.class).to(ShortNameFactory.class);
    bind(PortIdFactory.class).annotatedWith(VisJs.class).to(PortIdFactoryImpl.class);
  }
}
