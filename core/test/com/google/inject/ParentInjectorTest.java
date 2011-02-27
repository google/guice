/*
Copyright (C) 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.inject;

import static com.google.inject.Asserts.assertContains;

import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Iterables;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.TypeConverter;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ParentInjectorTest extends TestCase {

  public void testParentAndChildCannotShareExplicitBindings() {
    Injector parent = Guice.createInjector(bindsA);
    try {
      parent.createChildInjector(bindsA);
      fail("Created the same explicit binding on both parent and child");
    } catch (CreationException e) {
      assertContains(e.getMessage(), "A binding to ", A.class.getName(), " was already configured",
          " at ", getClass().getName(), ".configure(ParentInjectorTest.java:",
          " at ", getClass().getName(), ".configure(ParentInjectorTest.java:");
    }
  }

  public void testParentJitBindingWontClobberChildBinding() {
    Injector parent = Guice.createInjector();
    parent.createChildInjector(bindsA);
    try {
      parent.getInstance(A.class);
      fail("Created a just-in-time binding on the parent that's the same as a child's binding");
    } catch (ConfigurationException e) {
      assertContains(e.getMessage(),
          "Unable to create binding for " + A.class.getName(),
          "It was already configured on one or more child injectors or private modules",
          "bound at " + bindsA.getClass().getName() + ".configure(",
          "If it was in a PrivateModule, did you forget to expose the binding?",
          "while locating " + A.class.getName());
    }
  }
  
  public void testChildCannotBindToAParentJitBinding() {
    Injector parent = Guice.createInjector();
    parent.getInstance(A.class);
    try {
      parent.createChildInjector(bindsA);
      fail();
    } catch(CreationException ce) {
      assertContains(Iterables.getOnlyElement(ce.getErrorMessages()).getMessage(),
          "A just-in-time binding to " + A.class.getName() + " was already configured on a parent injector.");
    }    
  }

  public void testJustInTimeBindingsAreSharedWithParentIfPossible() {
    Injector parent = Guice.createInjector();
    Injector child = parent.createChildInjector();
    assertSame(child.getInstance(A.class), parent.getInstance(A.class));

    Injector anotherChild = parent.createChildInjector();
    assertSame(anotherChild.getInstance(A.class), parent.getInstance(A.class));

    Injector grandchild = child.createChildInjector();
    assertSame(grandchild.getInstance(A.class), parent.getInstance(A.class));
  }

  public void testBindingsInherited() {
    Injector parent = Guice.createInjector(bindsB);
    Injector child = parent.createChildInjector();
    assertSame(RealB.class, child.getInstance(B.class).getClass());
  }

  public void testGetParent() {
    Injector top = Guice.createInjector(bindsA);
    Injector middle = top.createChildInjector(bindsB);
    Injector bottom = middle.createChildInjector();
    assertSame(middle, bottom.getParent());
    assertSame(top, middle.getParent());
    assertNull(top.getParent());
  }

  public void testChildBindingsNotVisibleToParent() {
    Injector parent = Guice.createInjector();
    parent.createChildInjector(bindsB);
    try {
      parent.getBinding(B.class);
      fail();
    } catch (ConfigurationException expected) {
    }
  }

  public void testScopesInherited() {
    Injector parent = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindScope(MyScope.class, Scopes.SINGLETON);
      }
    });
    Injector child = parent.createChildInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(A.class).in(MyScope.class);
      }
    });
    assertSame(child.getInstance(A.class), child.getInstance(A.class));
  }

  /*if[AOP]*/
  private final org.aopalliance.intercept.MethodInterceptor returnNullInterceptor
      = new org.aopalliance.intercept.MethodInterceptor() {
    public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation) {
      return null;
    }
  };

  public void testInterceptorsInherited() {
    Injector parent = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        super.bindInterceptor(Matchers.any(), Matchers.returns(Matchers.identicalTo(A.class)),
            returnNullInterceptor);
      }
    });

    Injector child = parent.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(C.class);
      }
    });

    assertNull(child.getInstance(C.class).interceptedMethod());
  }
  /*end[AOP]*/

  public void testTypeConvertersInherited() {
    Injector parent = Guice.createInjector(bindListConverterModule);
    Injector child = parent.createChildInjector(bindStringNamedB);

    assertEquals(ImmutableList.of(), child.getInstance(Key.get(List.class, Names.named("B"))));
  }

  public void testTypeConvertersConflicting() {
    Injector parent = Guice.createInjector(bindListConverterModule);
    Injector child = parent.createChildInjector(bindListConverterModule, bindStringNamedB);

    try {
      child.getInstance(Key.get(List.class, Names.named("B")));
      fail();
    } catch (ConfigurationException expected) {
      Asserts.assertContains(expected.getMessage(), "Multiple converters can convert");
    }
  }

  public void testInjectorInjectionSpanningInjectors() {
    Injector parent = Guice.createInjector();
    Injector child = parent.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(D.class);
      }
    });

    D d = child.getInstance(D.class);
    assertSame(d.injector, child);

    E e = child.getInstance(E.class);
    assertSame(e.injector, parent);
  }

  public void testSeveralLayersOfHierarchy() {
    Injector top = Guice.createInjector(bindsA);
    Injector left = top.createChildInjector();
    Injector leftLeft = left.createChildInjector(bindsD);
    Injector right = top.createChildInjector(bindsD);
    
    assertSame(leftLeft, leftLeft.getInstance(D.class).injector);
    assertSame(right, right.getInstance(D.class).injector);
    assertSame(top, leftLeft.getInstance(E.class).injector);
    assertSame(top.getInstance(A.class), leftLeft.getInstance(A.class));

    Injector leftRight = left.createChildInjector(bindsD);
    assertSame(leftRight, leftRight.getInstance(D.class).injector);

    try {
      top.getInstance(D.class);
      fail();
    } catch (ConfigurationException expected) {
    }

    try {
      left.getInstance(D.class);
      fail();
    } catch (ConfigurationException expected) {
    }
  }

  public void testScopeBoundInChildInjectorOnly() {
    Injector parent = Guice.createInjector();
    Injector child = parent.createChildInjector(new AbstractModule() {
      protected void configure() {
        bindScope(MyScope.class, Scopes.SINGLETON);
      }
    });

    try {
      parent.getProvider(F.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "No scope is bound to com.google.inject.ParentInjectorTest$MyScope.",
          "at " + F.class.getName() + ".class(ParentInjectorTest.java",
          "  while locating " + F.class.getName());
    }
    
    assertNotNull(child.getProvider(F.class).get());
  }

  public void testErrorInParentButOkayInChild() {
    Injector parent = Guice.createInjector();
    Injector childInjector = parent.createChildInjector(new AbstractModule() {
      protected void configure() {
        bindScope(MyScope.class, Scopes.SINGLETON);
        bind(Object.class).to(F.class);
      }
    });
    Object one = childInjector.getInstance(Object.class);
    Object two = childInjector.getInstance(Object.class);
    assertSame(one, two);
  }

  public void testErrorInParentAndChild() {
    Injector parent = Guice.createInjector();
    Injector childInjector = parent.createChildInjector();

    try {
      childInjector.getInstance(G.class);
      fail();
    } catch(ConfigurationException expected) {
      assertContains(expected.getMessage(), "No scope is bound to " + MyScope.class.getName(),
          "at " + F.class.getName() + ".class(ParentInjectorTest.java:",
          "  while locating " + G.class.getName());
    }
  }

  @Singleton
  static class A {}

  private final Module bindsA = new AbstractModule() {
    protected void configure() {
      bind(A.class).toInstance(new A());
    }
  };

  interface B {}
  static class RealB implements B {}

  private final Module bindsB = new AbstractModule() {
    protected void configure() {
      bind(B.class).to(RealB.class);
    }
  };

  @Target(TYPE) @Retention(RUNTIME) @ScopeAnnotation
  public @interface MyScope {}

  private final TypeConverter listConverter = new TypeConverter() {
    public Object convert(String value, TypeLiteral<?> toType) {
      return ImmutableList.of();
    }
  };

  private final Module bindListConverterModule = new AbstractModule() {
    protected void configure() {
      convertToTypes(Matchers.any(), listConverter);
    }
  };

  private final Module bindStringNamedB = new AbstractModule() {
    protected void configure() {
      bind(String.class).annotatedWith(Names.named("B")).toInstance("buzz");
    }
  };

  public static class C {
    public A interceptedMethod() {
      return new A();
    }
  }

  static class D {
    @Inject Injector injector;
  }

  static class E {
    @Inject Injector injector;
  }

  private final Module bindsD = new AbstractModule() {
    protected void configure() {
      bind(D.class);
    }
  };

  @MyScope
  static class F implements G {}

  @ImplementedBy(F.class)
  interface G {}
}
