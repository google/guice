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

import com.google.common.collect.ImmutableList;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.TypeConverter;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.List;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

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
    } catch (ProvisionException e) {
      assertContains(e.getMessage(), "A binding to ", A.class.getName(),
          " already exists on a child injector.");
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

  public void testChildBindingsNotVisibleToParent() {
    Injector parent = Guice.createInjector();
    parent.createChildInjector(bindsB);
    try {
      parent.getBinding(B.class);
      fail();
    } catch (ProvisionException expected) {
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
    } catch (ProvisionException expected) {
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

  private final MethodInterceptor returnNullInterceptor = new MethodInterceptor() {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      return null;
    }
  };

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
}
