/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.example;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import static junit.framework.Assert.assertTrue;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ClientServiceWithGuice {

// 48 lines

public interface Service {
  void go();
}

public static class ServiceImpl implements Service {
  public void go() {
    // ...
  }
}

public static class MyModule extends AbstractModule {
  protected void configure() {
    bind(Service.class).to(ServiceImpl.class).in(Scopes.SINGLETON);
  }
}

public static class Client {

  private final Service service;

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
  client.go();
  assertTrue(mock.isGone());
}

public static class MockService implements Service {

  private boolean gone = false;

  public void go() {
    gone = true;
  }

  public boolean isGone() {
    return gone;
  }
}

public static void main(String[] args) throws CreationException {
  new ClientServiceWithGuice().testClient();
  Injector injector = Guice.createInjector(new MyModule());
  Client client = injector.getInstance(Client.class);
}
}
