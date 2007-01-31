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

package com.google.inject;

import junit.framework.TestCase;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class FactoryTest extends TestCase {

  public void testInjection() throws Exception {
    ContainerBuilder cb = new ContainerBuilder();

    // Called from getInstance().
    cb.bind(Foo.class).to(createFactory(Foo.class, "default", null));

    // Called during preloading.
    cb.bind(Bar.class)
        .named("fooBar")
        .to(createFactory(Bar.class, "fooBar", null))
        .in(Scopes.CONTAINER);

    cb.bind(Tee.class).named("tee1")
        .to(createFactory(Tee.class, "tee1",
            Bar.class.getDeclaredConstructor(Tee.class)));

    cb.bind(Tee.class).named("tee2").to(
        createFactory(Tee.class, "tee2", Bar.class.getDeclaredField("tee2")));

    final Method execute = Tee.class.getDeclaredMethod(
        "execute", Bob.class, Bob.class);
    cb.bind(Bob.class).named("bob1").to(
        createFactory(Bob.class, "bob1", execute));
    cb.bind(Bob.class).named("bob2").to(
        createFactory(Bob.class, "bob2", execute));

    Container c = cb.create(true);

    Foo foo = c.getFactory(Key.get(Foo.class)).get();

    assertNotNull(foo.bar);
    assertNotNull(foo.bar.tee1);
    assertNotNull(foo.bar.tee2);
    assertNotNull(foo.bar.tee1.bob1);
    assertNotNull(foo.bar.tee1.bob2);
    assertNotNull(foo.bar.tee2.bob1);
    assertNotNull(foo.bar.tee2.bob2);
  }

  <T> ContextualFactory<T> createFactory(
      final Class<T> type, final String name, final Member expectedMember) {
    return new ContextualFactory<T>() {
      public T get(Context context) {
        assertEquals(expectedMember, context.getMember());
        assertEquals(name, context.getKey().getName());
        assertEquals(type, context.getKey().getTypeToken().getType());
        return context.getContainer().getCreator(type).get();
      }
    };
  }

  static class Foo {
    final Bar bar;
    @Inject("fooBar") Foo(Bar bar) {
      this.bar = bar;
    }
  }

  static class Bar {
    @Inject("tee2") Tee tee2;
    final Tee tee1;
    @Inject("tee1") Bar(Tee tee1) {
      this.tee1 = tee1;
    }
  }

  static class Tee {
    Bob bob1, bob2;
    @Inject void execute(
        @Inject("bob1") Bob bob1,
        @Inject("bob2") Bob bob2) {
      this.bob1 = bob1;
      this.bob2 = bob2;
    }
  }

  static class Bob {}
}
