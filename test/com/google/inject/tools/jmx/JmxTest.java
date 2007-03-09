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

package com.google.inject.tools.jmx;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class JmxTest {

  interface Foo {}

  static class FooImpl implements Foo {}

  @Singleton
  static class TransactionalFoo implements Foo {}

  static class Bar {}

  @BindingAnnotation @Retention(RUNTIME)
  @interface Transactional {}

  public static void main(String[] args) throws Exception {
    Manager.main(new String[] { TestModule.class.getName() });
  }
  
  public static class TestModule extends AbstractModule {

    protected void configure() {
      bind(Foo.class).to(FooImpl.class);
      bind(Bar.class);
      bind(Foo.class)
          .annotatedWith(Transactional.class)
          .to(FooImpl.class);
      bindConstant().annotatedWith(Names.named("port")).to(8080);
      bind(Key.get(Object.class)).to(Key.get(Bar.class));
//      install(new ServletModule());
    }
  }
}
