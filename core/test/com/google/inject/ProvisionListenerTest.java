/**
 * Copyright (C) 2011 Google Inc.
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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.internal.util.ImmutableList.of;
import static com.google.inject.name.Names.named;

import java.util.List;

import junit.framework.TestCase;

import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Lists;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.spi.ProvisionListener;

/**
 * Tests for {@link Binder#bindListener(Matcher, ProvisionListener...)}
 * 
 * @author sameb@google.com (Sam Berlin)
 */
public class ProvisionListenerTest extends TestCase {

  public void testExceptionInListenerBeforeProvisioning() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), new FailBeforeProvision());
      }
    });
    try {
      injector.getInstance(Foo.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error notifying ProvisionListener " + FailBeforeProvision.class.getName()
          + " of " + Foo.class.getName(),
          "Reason: java.lang.RuntimeException: boo",
          "while locating " + Foo.class.getName());
      assertEquals("boo", pe.getCause().getMessage());
    }
  }
  
  public void testExceptionInListenerAfterProvisioning() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), new FailAfterProvision());
      }
    });
    try {
      injector.getInstance(Foo.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error notifying ProvisionListener " + FailAfterProvision.class.getName()
          + " of " + Foo.class.getName(),
          "Reason: java.lang.RuntimeException: boo",
          "while locating " + Foo.class.getName());
      assertEquals("boo", pe.getCause().getMessage());
    }
  }
  
  public void testExceptionInProvisionExplicitlyCalled() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), new JustProvision());
      }
    });
    try {
      injector.getInstance(FooBomb.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error injecting constructor, java.lang.RuntimeException: Retry, Abort, Fail",
          " at " + FooBomb.class.getName(),
          " while locating " + FooBomb.class.getName());
      assertEquals("Retry, Abort, Fail", pe.getCause().getMessage());
    }
  }
  
  public void testExceptionInProvisionAutomaticallyCalled() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), new NoProvision());
      }
    });
    try {
      injector.getInstance(FooBomb.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error injecting constructor, java.lang.RuntimeException: Retry, Abort, Fail",
          " at " + FooBomb.class.getName(),
          " while locating " + FooBomb.class.getName());
      assertEquals("Retry, Abort, Fail", pe.getCause().getMessage());
    }
  }
  
  public void testListenerCallsProvisionTwice() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), new ProvisionTwice());
      }
    });
    try {
      injector.getInstance(Foo.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error notifying ProvisionListener " + ProvisionTwice.class.getName()
          + " of " + Foo.class.getName(),
          "Reason: java.lang.IllegalStateException: Already provisioned in this listener.",
          "while locating " + Foo.class.getName());
      assertEquals("Already provisioned in this listener.", pe.getCause().getMessage());  
    }
  }
  
  public void testCachedInScopePreventsProvisionNotify() {
    final Counter count1 = new Counter();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), count1);
        bind(Foo.class).in(Scopes.SINGLETON);
      }
    });
    Foo foo = injector.getInstance(Foo.class);
    assertNotNull(foo);
    assertEquals(1, count1.count);
    
    // not notified the second time because nothing is provisioned
    // (it's cached in the scope)
    count1.count = 0;
    assertSame(foo, injector.getInstance(Foo.class));
    assertEquals(0, count1.count);
  }
  
  public void testCombineAllBindListenerCalls() {
    final Counter count1 = new Counter();
    final Counter count2 = new Counter();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), count1);
        bindListener(Matchers.any(), count2);
      }
    });
    assertNotNull(injector.getInstance(Foo.class));
    assertEquals(1, count1.count);
    assertEquals(1, count2.count);
  }
  
  public void testNotifyEarlyListenersIfFailBeforeProvision() {
    final Counter count1 = new Counter();
    final Counter count2 = new Counter();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), count1, new FailBeforeProvision(), count2);
      }
    });
    try {
      injector.getInstance(Foo.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error notifying ProvisionListener " + FailBeforeProvision.class.getName()
          + " of " + Foo.class.getName(),
          "Reason: java.lang.RuntimeException: boo",
          "while locating " + Foo.class.getName());
      assertEquals("boo", pe.getCause().getMessage());
      
      assertEquals(1, count1.count);
      assertEquals(0, count2.count);
    }
  }
  
  public void testNotifyLaterListenersIfFailAfterProvision() {
    final Counter count1 = new Counter();
    final Counter count2 = new Counter();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), count1, new FailAfterProvision(), count2);
      }
    });
    try {
      injector.getInstance(Foo.class);
      fail();
    } catch(ProvisionException pe) {
      assertEquals(1, pe.getErrorMessages().size());
      assertContains(pe.getMessage(),
          "1) Error notifying ProvisionListener " + FailAfterProvision.class.getName()
          + " of " + Foo.class.getName(),
          "Reason: java.lang.RuntimeException: boo",
          "while locating " + Foo.class.getName());
      assertEquals("boo", pe.getCause().getMessage());
      
      assertEquals(1, count1.count);
      assertEquals(1, count2.count);
    }
  }
  
  public void testNotifiedKeysOfAllBindTypes() {
    final Capturer capturer = new Capturer();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), capturer);
        bind(Foo.class).annotatedWith(named("pk")).toProvider(FooP.class);
        try {
          bind(Foo.class).annotatedWith(named("cxtr")).toConstructor(Foo.class.getDeclaredConstructor());
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
        bind(LinkedFoo.class).to(Foo.class);
      }
      
      @Provides @Named("pi") Foo provideFooBar() {
        return new Foo();
      }
    });
    
    // simple binding
    assertNotNull(injector.getInstance(Foo.class));
    assertEquals(of(Key.get(Foo.class)), capturer.getAndClear());
    
    // provider key binding -- notifies about provider & the object, always
    assertNotNull(injector.getInstance(Key.get(Foo.class, named("pk"))));
    assertEquals(of(Key.get(FooP.class), Key.get(Foo.class, named("pk"))), capturer.getAndClear());
    assertNotNull(injector.getInstance(Key.get(Foo.class, named("pk"))));
    assertEquals(of(Key.get(FooP.class), Key.get(Foo.class, named("pk"))), capturer.getAndClear());
    
    // JIT provider key binding -- notifies about provider & the object, always
    assertNotNull(injector.getInstance(JitFoo2.class));
    assertEquals(of(Key.get(JitFoo2P.class), Key.get(JitFoo2.class)), capturer.getAndClear());
    assertNotNull(injector.getInstance(JitFoo2.class));
    assertEquals(of(Key.get(JitFoo2P.class), Key.get(JitFoo2.class)), capturer.getAndClear());
    
    // provider instance binding -- just the object (not the provider)
    assertNotNull(injector.getInstance(Key.get(Foo.class, named("pi"))));
    assertEquals(of(Key.get(Foo.class, named("pi"))), capturer.getAndClear());
    
    // toConstructor binding
    assertNotNull(injector.getInstance(Key.get(Foo.class, named("cxtr"))));
    assertEquals(of(Key.get(Foo.class, named("cxtr"))), capturer.getAndClear());
    
    // linked binding -- notifies about the target (that's what's provisioned), not the link
    assertNotNull(injector.getInstance(LinkedFoo.class));
    assertEquals(of(Key.get(Foo.class)), capturer.getAndClear());

    // JIT linked binding -- notifies about the target (that's what's provisioned), not the link
    assertNotNull(injector.getInstance(JitFoo.class));
    assertEquals(of(Key.get(Foo.class)), capturer.getAndClear());
  }
  
  @ImplementedBy(Foo.class) static interface JitFoo {}  
  @ProvidedBy(JitFoo2P.class) static class JitFoo2 {}  
  static interface LinkedFoo {}
  static class Foo implements JitFoo, LinkedFoo {}
  static class FooP implements Provider<Foo> {
    public Foo get() {
      return new Foo();
    }
  }
  static class JitFoo2P implements Provider<JitFoo2> {
    public JitFoo2 get() {
      return new JitFoo2();
    }
  }
  
  static class FooBomb {
    FooBomb() {
      throw new RuntimeException("Retry, Abort, Fail");
    }
  }
  
  private static class Counter implements ProvisionListener {
    int count = 0;
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      count++;
    }
  }
  
  private static class Capturer implements ProvisionListener {
    List<Key> keys = Lists.newArrayList(); 
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      keys.add(provision.getKey());
      T provisioned = provision.provision();
      assertEquals(provision.getKey().getTypeLiteral().getRawType(), provisioned.getClass());
    }
    
    List<Key> getAndClear() {
      List<Key> copy = ImmutableList.copyOf(keys);
      keys.clear();
      return copy;
    }
  }

  private static class FailBeforeProvision implements ProvisionListener {
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      throw new RuntimeException("boo");
    }
  }  
  private static class FailAfterProvision implements ProvisionListener {
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      provision.provision();
      throw new RuntimeException("boo");
    }
  }  
  private static class JustProvision implements ProvisionListener {
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      provision.provision();
    }
  }  
  private static class NoProvision implements ProvisionListener {
    public <T> void onProvision(ProvisionInvocation<T> provision) {
    }
  }
  private static class ProvisionTwice implements ProvisionListener {
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      provision.provision();
      provision.provision();
    }
  }
}
