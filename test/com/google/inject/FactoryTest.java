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
    cb.factory(Foo.class, createFactory(Foo.class, "default", null));

    // Called during singleton loading.
    cb.factory(Bar.class, "fooBar",
        createFactory(Bar.class, "fooBar", null), Scope.SINGLETON);

    cb.factory(Tee.class, "tee1",
        createFactory(Tee.class, "tee1",
            Bar.class.getDeclaredConstructor(Tee.class)));

    cb.factory(Tee.class, "tee2",
        createFactory(Tee.class, "tee2",
            Bar.class.getDeclaredField("tee2")));

    final Method execute = Tee.class.getDeclaredMethod(
        "execute", Bob.class, Bob.class);
    cb.factory(Bob.class, "bob1",
        createFactory(Bob.class, "bob1", execute));
    cb.factory(Bob.class, "bob2",
        createFactory(Bob.class, "bob2", execute));

    Container c = cb.create(true);

    Foo foo = c.getInstance(Foo.class);

    assertNotNull(foo.bar);
    assertNotNull(foo.bar.tee1);
    assertNotNull(foo.bar.tee2);
    assertNotNull(foo.bar.tee1.bob1);
    assertNotNull(foo.bar.tee1.bob2);
    assertNotNull(foo.bar.tee2.bob1);
    assertNotNull(foo.bar.tee2.bob2);
  }

  <T> Factory<T> createFactory(final Class<T> type, final String name,
      final Member expectedMember) {
    return new Factory<T>() {
      public T create(Context context) throws Exception {
        assertEquals(expectedMember, context.getMember());
        assertEquals(name, context.getName());
        assertEquals(type, context.getType());
        return context.getContainer().inject(type);
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
