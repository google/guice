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

import static junit.framework.Assert.assertTrue;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ClientServiceWithDependencyInjection {

// 62 lines

public interface Service {
  void go();
}

public static class ServiceImpl implements ClientServiceWithDependencyInjection.Service {
  public void go() {
    // ...
  }
}

public static class ServiceFactory {

  private ServiceFactory() {}

  private static final Service service = new ServiceImpl();

  public static Service getInstance() {
    return service;
  }
}

public static class Client {

  private final Service service;

  public Client(Service service) {
    this.service = service;
  }

  public void go() {
    service.go();
  }
}

public static class ClientFactory {

  private ClientFactory() {}

  public static Client getInstance() {
    Service service = ServiceFactory.getInstance();
    return new Client(service);
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

  public static void main(String[] args) {
    new ClientServiceWithDependencyInjection().testClient();
  }
}
