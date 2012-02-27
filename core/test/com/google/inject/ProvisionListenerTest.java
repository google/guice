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

import static com.google.common.collect.ImmutableList.of;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.name.Names.named;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.spi.DependencyAndSource;
import com.google.inject.spi.ProvisionListener;

import junit.framework.TestCase;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
  
  public void testSingletonMatcher() {
    final Counter counter = new Counter();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(new AbstractMatcher<Binding<?>>() {
          @Override
          public boolean matches(Binding<?> t) {
            return Scopes.isSingleton(t);
          }
        }, counter);
      }
    });
    assertEquals(0, counter.count);
    // no increment for getting Many.
    injector.getInstance(Many.class);
    assertEquals(0, counter.count);
    // but an increment for getting Sole, since it's a singleton.
    injector.getInstance(Sole.class);
    assertEquals(1, counter.count);
  }
  
  public void testCallingBindingDotGetProviderDotGet() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(), new ProvisionListener() {
          @Override
          public <T> void onProvision(ProvisionInvocation<T> provision) {
            provision.getBinding().getProvider().get(); // AGH!
          }
        });
      }
    });
    
    try {
      injector.getInstance(Sole.class);
      fail();
    } catch(ProvisionException expected) {
      // We don't really care what kind of error you get, we only care you get an error.
    }
    
    try {
      injector.getInstance(Many.class);
      fail();
    } catch(ProvisionException expected) {
      // We don't really care what kind of error you get, we only care you get an error.
    }
  }
  
  @Singleton static class Sole {}
  static class Many {}
  
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
      keys.add(provision.getBinding().getKey());
      T provisioned = provision.provision();
      assertEquals(provision.getBinding().getKey().getRawType(), provisioned.getClass());
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
  
  private static class ChainAsserter implements ProvisionListener {
    private final List<Class<?>> provisionList;
    private final List<Class<?>> expected;
    
    public ChainAsserter(List<Class<?>> provisionList, Iterable<Class<?>> expected) {
      this.provisionList = provisionList;
      this.expected = ImmutableList.copyOf(expected);
    }
    
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      List<Class<?>> actual = Lists.newArrayList();
      for (DependencyAndSource dep : provision.getDependencyChain()) {
        actual.add(dep.getDependency().getKey().getRawType());
      }
      assertEquals(expected, actual);
      provisionList.add(provision.getBinding().getKey().getRawType());
    }
  }
  
  private static Matcher<Binding<?>> keyMatcher(final Class<?> clazz) {
    return new AbstractMatcher<Binding<?>>() {
      @Override
      public boolean matches(Binding<?> t) {
        return t.getKey().equals(Key.get(clazz));
      }
    };
  }
  
  @SuppressWarnings("unchecked")
  public void testDependencyChain() {
    final List<Class<?>> pList = Lists.newArrayList();
    final List<Class<?>> totalList = Lists.newArrayList();
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Instance.class).toInstance(new Instance());
        bind(B.class).to(BImpl.class);
        bind(D.class).toProvider(DP.class);
        
        bindListener(Matchers.any(), new ProvisionListener() {
          public <T> void onProvision(ProvisionInvocation<T> provision) {
            totalList.add(provision.getBinding().getKey().getRawType());
          }
        });
        
        // Build up a list of asserters for our dependency chains.
        ImmutableList.Builder<Class<?>> chain = ImmutableList.builder();
        
        chain.add(Instance.class).add(A.class);
        bindListener(keyMatcher(A.class), new ChainAsserter(pList, chain.build()));
        
        chain.add(B.class).add(BImpl.class);
        bindListener(keyMatcher(BImpl.class), new ChainAsserter(pList, chain.build()));
        
        chain.add(C.class);
        bindListener(keyMatcher(C.class), new ChainAsserter(pList, chain.build()));
        
        // the chain has D before DP even though DP is provisioned & notified first
        // because we do DP because of D, and need DP to provision D.
        chain.add(D.class).add(DP.class);
        bindListener(keyMatcher(D.class), new ChainAsserter(pList, chain.build()));
        bindListener(keyMatcher(DP.class), new ChainAsserter(pList, chain.build()));
        
        chain.add(E.class);
        bindListener(keyMatcher(E.class), new ChainAsserter(pList, chain.build()));
        
        chain.add(F.class);
        bindListener(keyMatcher(F.class), new ChainAsserter(pList, chain.build()));
      }
      @Provides C c(D d) {
        return new C() {};
      }
    });
    Instance instance = injector.getInstance(Instance.class);
    // make sure we're checking all of the chain asserters..
    assertEquals(of(A.class, BImpl.class, C.class, DP.class, D.class, E.class, F.class),
        pList);
    // and make sure that nothing else was notified that we didn't expect.
    assertEquals(totalList, pList);
  }
  
  public void testModuleRequestInjection() {
    final AtomicBoolean notified = new AtomicBoolean();    
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        requestInjection(new Object() {
          @Inject Foo foo;
        });
        bindListener(Matchers.any(),
            new SpecialChecker(Foo.class, getClass().getName() + ".configure(", notified));
      }
    });
    assertTrue(notified.get());
  }
  
  public void testToProviderInstance() {
    final AtomicBoolean notified = new AtomicBoolean();    
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Object.class).toProvider(new Provider<Object>() {
          @Inject Foo foo;
          public Object get() {
            return null;
          }
        });
        bindListener(Matchers.any(),
            new SpecialChecker(Foo.class, getClass().getName() + ".configure(", notified));
      }
    });
    assertTrue(notified.get());
  }
  
  public void testInjectorInjectMembers() {
    final Object object = new Object() {
      @Inject Foo foo;
    };
    final AtomicBoolean notified = new AtomicBoolean();    
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindListener(Matchers.any(),
            new SpecialChecker(Foo.class, object.getClass().getName(), notified));
      }
    }).injectMembers(object);
    assertTrue(notified.get());
  }
  
  private static class SpecialChecker implements ProvisionListener {
    private final Class<?> notifyType;
    private final String firstSource;
    private final AtomicBoolean notified;
    
    public SpecialChecker(Class<?> notifyType, String firstSource, AtomicBoolean notified) {
      this.notifyType = notifyType;
      this.firstSource = firstSource;
      this.notified = notified;
    }
    
    public <T> void onProvision(ProvisionInvocation<T> provision) {
      notified.set(true);
      assertEquals(notifyType, provision.getBinding().getKey().getRawType());            
      assertEquals(2, provision.getDependencyChain().size());
      
      assertEquals(null, provision.getDependencyChain().get(0).getDependency());
      assertContains(provision.getDependencyChain().get(0).getBindingSource(), firstSource);
      
      assertEquals(notifyType,
          provision.getDependencyChain().get(1).getDependency().getKey().getRawType());
      assertContains(provision.getDependencyChain().get(1).getBindingSource(),
          notifyType.getName() + ".class(");
    }
  }
  
  private static class Instance {
    @Inject A a;
  }
  private static class A {
    @Inject A(B b) {}
  }
  private interface B {}
  private static class BImpl implements B {
    @Inject void inject(C c) {}
  }
  private interface C {}
  private interface D {}
  private static class DP implements Provider<D> {
    @Inject Provider<E> ep;
    public D get() {
      ep.get();
      return new D() {};
    }
  }
  private static class E {
    @SuppressWarnings("unused")
    @Inject F f;
  }
  private static class F {}
}
