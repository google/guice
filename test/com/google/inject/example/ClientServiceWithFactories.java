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
public class ClientServiceWithFactories {

// 72 lines

public interface Service {
  void go();
}

public static class ServiceImpl implements Service {
  public void go() {
    // ...
  }
}

public interface Factory<T> {
  T get();
}

public static class ServiceFactory {

  private ServiceFactory() {}

  private static Factory<Service> instance = new Factory<Service>() {
    private final Service service = new ServiceImpl();
    public Service get() {
      return service;
    }
  };

  public static Factory<Service> getInstance() {
    return instance;
  }

  public static void setInstance(Factory<Service> serviceFactory) {
    instance = serviceFactory;
  }
}

public static class Client {

  public void go() {
    Factory<Service> factory = ServiceFactory.getInstance();
    Service service = factory.get();
    service.go();
  }
}

public void testClient() {
  Factory<Service> previous = ServiceFactory.getInstance();
  try {
    final MockService mock = new MockService();
    ServiceFactory.setInstance(new Factory<Service>() {
      public Service get() {
        return mock;
      }
    });
    Client client = new Client();
    client.go();
    assertTrue(mock.isGone());
  }
  finally {
    ServiceFactory.setInstance(previous);
  }
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
    new ClientServiceWithFactories().testClient();
  }
}
