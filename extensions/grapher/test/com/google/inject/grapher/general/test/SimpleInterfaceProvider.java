package com.google.inject.grapher.general.test;

import javax.inject.Provider;

/**
 * @author ksaric
 */
public class SimpleInterfaceProvider implements Provider<SimpleInterface> {
  @Override
  public SimpleInterface get() {
    return new TestSimpleInterface();
  }
}
