/**
 * Copyright (C) 2010 Google Inc.
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
import static com.google.inject.name.Names.named;

import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Objects;
import com.google.inject.name.Named;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;

import junit.framework.TestCase;

import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.util.Providers;

/**
 * A suite of tests for duplicate bindings.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
public class DuplicateBindingsTest extends TestCase {
  
  private FooImpl foo = new FooImpl();
  private Provider<Foo> pFoo = Providers.<Foo>of(new FooImpl());
  private Class<? extends Provider<? extends Foo>> pclFoo = FooProvider.class;
  private Class<? extends Foo> clFoo = FooImpl.class;
  private Constructor<FooImpl> cFoo = FooImpl.cxtor();

  public void testDuplicateBindingsAreIgnored() {
    Injector injector = Guice.createInjector(
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo),
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo)
    );
    List<Key<?>> bindings = Lists.newArrayList(injector.getAllBindings().keySet());
    removeBasicBindings(bindings);
    
    // Ensure only one binding existed for each type.
    assertTrue(bindings.remove(Key.get(Foo.class, named("instance"))));
    assertTrue(bindings.remove(Key.get(Foo.class, named("pInstance"))));
    assertTrue(bindings.remove(Key.get(Foo.class, named("pKey"))));
    assertTrue(bindings.remove(Key.get(Foo.class, named("linkedKey"))));
    assertTrue(bindings.remove(Key.get(FooImpl.class)));
    assertTrue(bindings.remove(Key.get(Foo.class, named("constructor"))));
    assertTrue(bindings.remove(Key.get(FooProvider.class))); // JIT binding
    assertTrue(bindings.remove(Key.get(Foo.class, named("providerMethod"))));
    
    assertEquals(bindings.toString(), 0, bindings.size());
  }
  
  public void testElementsDeduplicate() {
    List<Element> elements = Elements.getElements(
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo),
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo)
    );
    assertEquals(14, elements.size());
    assertEquals(7, new LinkedHashSet<Element>(elements).size());
  }
  
  public void testProviderMethodsFailIfInstancesDiffer() {
    try {
      Guice.createInjector(new FailingProviderModule(), new FailingProviderModule());
      fail("should have failed");
    } catch(CreationException ce) {
      assertContains(ce.getMessage(),
          "A binding to " + Foo.class.getName() + " was already configured at " + FailingProviderModule.class.getName(),
          "at " + FailingProviderModule.class.getName()
          );
    }
  }
  
  public void testSameScopeInstanceIgnored() {
    Guice.createInjector(
        new ScopedModule(Scopes.SINGLETON, foo, pFoo, pclFoo, clFoo, cFoo),
        new ScopedModule(Scopes.SINGLETON, foo, pFoo, pclFoo, clFoo, cFoo)
    );
    
    Guice.createInjector(
        new ScopedModule(Scopes.NO_SCOPE, foo, pFoo, pclFoo, clFoo, cFoo),
        new ScopedModule(Scopes.NO_SCOPE, foo, pFoo, pclFoo, clFoo, cFoo)
    );
  }
  
  public void testSameScopeAnnotationIgnored() {
    Guice.createInjector(
        new AnnotatedScopeModule(Singleton.class, foo, pFoo, pclFoo, clFoo, cFoo),
        new AnnotatedScopeModule(Singleton.class, foo, pFoo, pclFoo, clFoo, cFoo)
    );
  }
  
  public void testMixedAnnotationAndScopeForSingletonIgnored() {
    Guice.createInjector(
        new ScopedModule(Scopes.SINGLETON, foo, pFoo, pclFoo, clFoo, cFoo),
        new AnnotatedScopeModule(Singleton.class, foo, pFoo, pclFoo, clFoo, cFoo)
    );
  }
  
  public void testMixedScopeAndUnscopedIgnored() {
    Guice.createInjector(
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo),
        new ScopedModule(Scopes.NO_SCOPE, foo, pFoo, pclFoo, clFoo, cFoo)
    );
  }
  
  public void testMixedScopeFails() {
    try {
      Guice.createInjector(
          new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo),
          new ScopedModule(Scopes.SINGLETON, foo, pFoo, pclFoo, clFoo, cFoo)
      );
      fail("expected exception");
    } catch(CreationException ce) {
      assertContains(ce.getMessage(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("pInstance") + " was already configured at " + SimpleModule.class.getName(),
          "at " + ScopedModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("pKey") + " was already configured at " + SimpleModule.class.getName(),
          "at " + ScopedModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("linkedKey") + " was already configured at " + SimpleModule.class.getName(),
          "at " + ScopedModule.class.getName(), 
          "A binding to " + FooImpl.class.getName() + " was already configured at " + SimpleModule.class.getName(),
          "at " + ScopedModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("constructor") + " was already configured at " + SimpleModule.class.getName(),
          "at " + ScopedModule.class.getName());
    }
  }
  
  @SuppressWarnings("unchecked")
  public void testMixedTargetsFails() {
    try {
      Guice.createInjector(
          new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo),
          new SimpleModule(new FooImpl(), Providers.<Foo>of(new FooImpl()), 
              (Class)BarProvider.class, (Class)Bar.class, (Constructor)Bar.cxtor())
      );
      fail("expected exception");
    } catch(CreationException ce) {
      assertContains(ce.getMessage(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("pInstance") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("pKey") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("linkedKey") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(),
          "A binding to " + Foo.class.getName() + " annotated with " + named("constructor") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName());
    }
  }
  
  public void testExceptionInEqualsThrowsCreationException() {
    try {
      Guice.createInjector(new ThrowingModule(), new ThrowingModule());
      fail("expected exception");
    } catch(CreationException ce) {
      assertContains(ce.getMessage(),
          "A binding to " + Foo.class.getName() + " was already configured at " + ThrowingModule.class.getName(),
          "and an error was thrown while checking duplicate bindings.  Error: java.lang.RuntimeException: Boo!",
          "at " + ThrowingModule.class.getName());
    }
  }
  
  public void testChildInjectorDuplicateParentFail() {
    Injector injector = Guice.createInjector(
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo)
    );
    
    try {
      injector.createChildInjector(
          new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo)
      );
      fail("expected exception");
    } catch(CreationException ce) {
      assertContains(ce.getMessage(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("pInstance") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("pKey") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(), 
          "A binding to " + Foo.class.getName() + " annotated with " + named("linkedKey") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(),
          "A binding to " + Foo.class.getName() + " annotated with " + named("constructor") + " was already configured at " + SimpleModule.class.getName(),
          "at " + SimpleModule.class.getName(),
          "A binding to " + Foo.class.getName() + " annotated with " + named("providerMethod") + " was already configured at " + SimpleProviderModule.class.getName(),
          "at " + SimpleProviderModule.class.getName()
          );
    } 
    
    
  }
  
  public void testDuplicatesSolelyInChildIgnored() {
    Injector injector = Guice.createInjector();
    injector.createChildInjector(
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo),
        new SimpleModule(foo, pFoo, pclFoo, clFoo, cFoo)
    );
  }
  
  public void testDifferentBindingTypesFail() {
    List<Element> elements = Elements.getElements(
        new FailedModule(foo, pFoo, pclFoo, clFoo, cFoo)
    );
    
    // Make sure every combination of the elements with another element fails.
    // This ensures that duplication checks the kind of binding also.
    for(Element e1 : elements) {
      for(Element e2: elements) {
        // if they're the same, this shouldn't fail.
        try {
          Guice.createInjector(Elements.getModule(Arrays.asList(e1, e2)));
          if(e1 != e2) {
            fail("must fail!");
          }
        } catch(CreationException expected) {
          if(e1 != e2) {
            assertContains(expected.getMessage(),
                "A binding to " + Foo.class.getName() + " was already configured at " + FailedModule.class.getName(),
                "at " + FailedModule.class.getName());
          } else {
            throw expected;
          }
        }
      }
    }
  }
  
  public void testJitBindingsAreCheckedAfterConversions() {
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
       bind(A.class);
       bind(A.class).to(RealA.class);
      }
    });
  }
  
  public void testEqualsNotCalledByDefaultOnInstance() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnEquals = true;
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
       bind(String.class);
       bind(HashEqualsTester.class).toInstance(a);
      }
    });
  }
  
  public void testEqualsNotCalledByDefaultOnProvider() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnEquals = true;
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
       bind(String.class);
       bind(Object.class).toProvider(a);
      }
    });
  }
  
  public void testHashcodeNeverCalledOnInstance() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnHashcode = true;
    a.equality = "test";
    
    final HashEqualsTester b = new HashEqualsTester();
    b.throwOnHashcode = true;
    b.equality = "test";
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
       bind(String.class);
       bind(HashEqualsTester.class).toInstance(a);
       bind(HashEqualsTester.class).toInstance(b);
      }
    });
  }
  
  public void testHashcodeNeverCalledOnProviderInstance() {
    final HashEqualsTester a = new HashEqualsTester();
    a.throwOnHashcode = true;
    a.equality = "test";
    
    final HashEqualsTester b = new HashEqualsTester();
    b.throwOnHashcode = true;
    b.equality = "test";
    Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
       bind(String.class);
       bind(Object.class).toProvider(a);
       bind(Object.class).toProvider(b);
      }
    });
  }

  private static class RealA extends A {}
  @ImplementedBy(RealA.class) private static class A {}
  
  private void removeBasicBindings(Collection<Key<?>> bindings) {
    bindings.remove(Key.get(Injector.class));
    bindings.remove(Key.get(Logger.class));
    bindings.remove(Key.get(Stage.class));
  }
  
  private static class ThrowingModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(Foo.class).toInstance(new Foo() {
        @Override
        public boolean equals(Object obj) {
          throw new RuntimeException("Boo!");
        }
      });
    }
  }
  
  private static abstract class FooModule extends AbstractModule {
    protected final FooImpl foo;
    protected final Provider<Foo> pFoo;
    protected final Class<? extends Provider<? extends Foo>> pclFoo;
    protected final Class<? extends Foo> clFoo;
    protected final Constructor<FooImpl> cFoo;
    
    FooModule(FooImpl foo, Provider<Foo> pFoo, Class<? extends Provider<? extends Foo>> pclFoo,
        Class<? extends Foo> clFoo, Constructor<FooImpl> cFoo) {
      this.foo = foo;
      this.pFoo = pFoo;
      this.pclFoo = pclFoo;
      this.clFoo = clFoo;
      this.cFoo = cFoo;
    }    
  }
  
  private static class FailedModule extends FooModule {
    FailedModule(FooImpl foo, Provider<Foo> pFoo, Class<? extends Provider<? extends Foo>> pclFoo,
        Class<? extends Foo> clFoo, Constructor<FooImpl> cFoo) {
      super(foo, pFoo, pclFoo, clFoo, cFoo);
    }
    
    protected void configure() {
      // InstanceBinding
      bind(Foo.class).toInstance(foo);
      
      // ProviderInstanceBinding
      bind(Foo.class).toProvider(pFoo);
      
      // ProviderKeyBinding
      bind(Foo.class).toProvider(pclFoo);
      
      // LinkedKeyBinding
      bind(Foo.class).to(clFoo);
      
      // ConstructorBinding
      bind(Foo.class).toConstructor(cFoo);
    }
    
    @Provides Foo foo() {
      return null;
    }
  }
  
  private static class FailingProviderModule extends AbstractModule {
    @Override protected void configure() {}

    @Provides Foo foo() {
      return null;
    }
  }

  private static class SimpleProviderModule extends AbstractModule {
    @Override protected void configure() {}

    @Provides @Named("providerMethod") Foo foo() {
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      return obj.getClass() == getClass();
    }
  }  
  
  private static class SimpleModule extends FooModule {
    SimpleModule(FooImpl foo, Provider<Foo> pFoo, Class<? extends Provider<? extends Foo>> pclFoo,
        Class<? extends Foo> clFoo, Constructor<FooImpl> cFoo) {
      super(foo, pFoo, pclFoo, clFoo, cFoo);
    }
    
    protected void configure() {
      // InstanceBinding
      bind(Foo.class).annotatedWith(named("instance")).toInstance(foo);
      
      // ProviderInstanceBinding
      bind(Foo.class).annotatedWith(named("pInstance")).toProvider(pFoo);
      
      // ProviderKeyBinding
      bind(Foo.class).annotatedWith(named("pKey")).toProvider(pclFoo);
      
      // LinkedKeyBinding
      bind(Foo.class).annotatedWith(named("linkedKey")).to(clFoo);
      
      // UntargettedBinding / ConstructorBinding
      bind(FooImpl.class);
      
      // ConstructorBinding
      bind(Foo.class).annotatedWith(named("constructor")).toConstructor(cFoo);

      // ProviderMethod
      // (reconstructed from an Element to ensure it doesn't get filtered out
      //  by deduplicating Modules)
      install(Elements.getModule(Elements.getElements(new SimpleProviderModule())));
    }
  }
  
  private static class ScopedModule extends FooModule {
    private final Scope scope;

    ScopedModule(Scope scope, FooImpl foo, Provider<Foo> pFoo,
        Class<? extends Provider<? extends Foo>> pclFoo, Class<? extends Foo> clFoo,
        Constructor<FooImpl> cFoo) {
      super(foo, pFoo, pclFoo, clFoo, cFoo);
      this.scope = scope;
    }
    
    protected void configure() {
      // ProviderInstanceBinding
      bind(Foo.class).annotatedWith(named("pInstance")).toProvider(pFoo).in(scope);
      
      // ProviderKeyBinding
      bind(Foo.class).annotatedWith(named("pKey")).toProvider(pclFoo).in(scope);
      
      // LinkedKeyBinding
      bind(Foo.class).annotatedWith(named("linkedKey")).to(clFoo).in(scope);
      
      // UntargettedBinding / ConstructorBinding
      bind(FooImpl.class).in(scope);
      
      // ConstructorBinding
      bind(Foo.class).annotatedWith(named("constructor")).toConstructor(cFoo).in(scope);
    }
  }
  
  private static class AnnotatedScopeModule extends FooModule {
    private final Class<? extends Annotation> scope;

    AnnotatedScopeModule(Class<? extends Annotation> scope, FooImpl foo, Provider<Foo> pFoo,
        Class<? extends Provider<? extends Foo>> pclFoo, Class<? extends Foo> clFoo,
        Constructor<FooImpl> cFoo) {
      super(foo, pFoo, pclFoo, clFoo, cFoo);
      this.scope = scope;
    }
    
    
    protected void configure() {
      // ProviderInstanceBinding
      bind(Foo.class).annotatedWith(named("pInstance")).toProvider(pFoo).in(scope);
      
      // ProviderKeyBinding
      bind(Foo.class).annotatedWith(named("pKey")).toProvider(pclFoo).in(scope);
      
      // LinkedKeyBinding
      bind(Foo.class).annotatedWith(named("linkedKey")).to(clFoo).in(scope);
      
      // UntargettedBinding / ConstructorBinding
      bind(FooImpl.class).in(scope);
      
      // ConstructorBinding
      bind(Foo.class).annotatedWith(named("constructor")).toConstructor(cFoo).in(scope);
    }
  }  
  
  private static interface Foo {}
  private static class FooImpl implements Foo {
    @Inject public FooImpl() {}
    
    private static Constructor<FooImpl> cxtor() {
      try {
        return FooImpl.class.getConstructor();
      } catch (SecurityException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }  
  private static class FooProvider implements Provider<Foo> {
    public Foo get() {
      return new FooImpl();
    }
  }
  
  private static class Bar implements Foo {
    @Inject public Bar() {}
    
    private static Constructor<Bar> cxtor() {
      try {
        return Bar.class.getConstructor();
      } catch (SecurityException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }  
  private static class BarProvider implements Provider<Foo> {
    public Foo get() {
      return new Bar();
    }
  }
  
  private static class HashEqualsTester implements Provider<Object> {
    private String equality;
    private boolean throwOnEquals;
    private boolean throwOnHashcode;
    
    @Override
    public boolean equals(Object obj) {
      if (throwOnEquals) {
        throw new RuntimeException();
      } else if (obj instanceof HashEqualsTester) {
        HashEqualsTester o = (HashEqualsTester)obj;
        if(o.throwOnEquals) {
          throw new RuntimeException();
        }
        if(equality == null && o.equality == null) {
          return this == o;
        } else {
          return Objects.equal(equality, o.equality);
        }
      } else {
        return false;
      }
    }
    
    @Override
    public int hashCode() {
      if(throwOnHashcode) {
        throw new RuntimeException();
      } else {
        return super.hashCode();
      }
    }
    
    public Object get() {
      return new Object();
    }
  }
  
}
