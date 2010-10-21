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

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import junit.framework.Assert;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ClientServiceWithGuiceDefaults {

// 44 lines

@ImplementedBy(ServiceImpl.class)
public interface Service {
  void go();
}

@Singleton
public static class ServiceImpl implements ClientServiceWithGuiceDefaults.Service {
  public void go() {
    // ...
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
  Assert.assertTrue(mock.isGone());
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
  new ClientServiceWithGuiceDefaults().testClient();
  Injector injector = Guice.createInjector();
  Client client = injector.getProvider(Client.class).get();
}
}
