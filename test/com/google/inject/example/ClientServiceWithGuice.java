// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.example;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import static junit.framework.Assert.assertTrue;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ClientServiceWithGuice {

public interface Service {
  void go();
}

public static class ServiceImpl implements Service {
  public void go() {
    // ...
  }
}

public static class ServiceModule extends AbstractModule {
  protected void configure() {
    bind(Service.class).to(ServiceImpl.class);
  }
}

public static class Client {

  final Service service;

  @Inject
  public Client(Service service) {
    this.service = service;
  }

  public void go() {
    service.go();
  }
}

public void testClient() {
  MockService mock = new MockService();
  Client client = new Client(mock);
  assertTrue(mock.isGone());
}

public static class MockService implements Service {

  boolean gone = false;

  public void go() {
    gone = true;
  }

  public boolean isGone() {
    return gone;
  }
}

public static void main(String[] args) throws CreationException {
  new ClientServiceWithGuice().testClient();

  Injector injector = Guice.createInjector(new ServiceModule());
  Client client = injector.getInstance(Client.class);
}
}
