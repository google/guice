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

public interface Service {
  void go();
}

public static class ServiceImpl implements Service {
  public void go() {
    // ...
  }
}

public static abstract class ServiceFactory {

  public abstract Service getService();

  static ServiceFactory instance = new DefaultServiceFactory();

  public static ServiceFactory getInstance() {
    return instance;
  }

  public static void setInstance(ServiceFactory serviceFactory) {
    instance = serviceFactory;
  }
}

public static class DefaultServiceFactory extends ServiceFactory {
  public Service getService() {
    return new ServiceImpl();
  }
}

static class Client {

  final Service service;

  Client() {
    this.service = ServiceFactory.getInstance().getService();
  }

  void go() {
    service.go();
  }
}

public void testClient() {
  ServiceFactory previous = ServiceFactory.getInstance();
  try {
    MockService mock = new MockService();
    ServiceFactory.setInstance(new SingletonServiceFactory(mock));
    Client client = new Client();
    client.go();
    assertTrue(mock.isGone());
  }
  finally {
    ServiceFactory.setInstance(previous);
  }
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

public static class SingletonServiceFactory extends ServiceFactory {

  final Service service;

  public SingletonServiceFactory(Service service) {
    this.service = service;
  }

  public Service getService() {
    return this.service;
  }
}

public static void main(String[] args) {
  new ClientServiceWithFactories().testClient();
}
}
