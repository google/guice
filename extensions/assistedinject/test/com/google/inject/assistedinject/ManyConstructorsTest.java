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

package com.google.inject.assistedinject;

import junit.framework.TestCase;

import com.google.inject.AbstractModule;
import com.google.inject.Asserts;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * @author sameb@google.com (Sam Berlin)
 */
public class ManyConstructorsTest extends TestCase {
  
  public void testTwoConstructors() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(Factory.class));
      }
    });
    Factory factory = injector.getInstance(Factory.class);
    Foo noIndex = factory.create("no index");
    assertEquals("no index", noIndex.name);
    assertEquals(null, noIndex.index);
    Foo index = factory.create("index", 1);
    assertEquals("index", index.name);
    assertEquals(1, index.index.intValue());
  }
  
  public void testDifferentOrderParameters() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(OtherFactory.class));
      }
    });
    OtherFactory factory = injector.getInstance(OtherFactory.class);
    Foo noIndex = factory.create("no index");
    assertEquals("no index", noIndex.name);
    assertEquals(null, noIndex.index);
    Foo index = factory.create(1, "index");
    assertEquals("index", index.name);
    assertEquals(1, index.index.intValue());
    Foo index2 = factory.create("index", 2);
    assertEquals("index", index2.name);
    assertEquals(2, index2.index.intValue());
  }
  
  public void testInterfaceToImpl() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder()
          .implement(Bar.class, Foo.class)
          .build(BarFactory.class));
      }
    });
    BarFactory factory = injector.getInstance(BarFactory.class);
    Bar noIndex = factory.create("no index");
    assertEquals("no index", noIndex.getName());
    assertEquals(null, noIndex.getIndex());
    Bar index = factory.create("index", 1);
    assertEquals("index", index.getName());
    assertEquals(1, index.getIndex().intValue());
  }
  
  public void testUsingOneConstructor() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(SimpleFactory.class));
      }
    });
    SimpleFactory factory = injector.getInstance(SimpleFactory.class);
    Foo noIndex = factory.create("no index");
    assertEquals("no index", noIndex.name);
    assertEquals(null, noIndex.index);
    
    injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(SimpleFactory2.class));
      }
    });
    SimpleFactory2 factory2 = injector.getInstance(SimpleFactory2.class);
    Foo index = factory2.create("index", 1);
    assertEquals("index", index.name);
    assertEquals(1, index.index.intValue());
  }
  
  public void testTooManyMatchingConstructors() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          install(new FactoryModuleBuilder()
            .implement(Foo.class, TooManyMatches.class)
            .build(SimpleFactory2.class));
        }
      });
      fail("should have failed");
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + TooManyMatches.class.getName()
          + " has more than one constructor annotated with @AssistedInject that "
          + "matches the parameters in method " + SimpleFactory2.class.getName());
    }
  }

  public void testNoMatchingConstructorsBecauseTooManyParams() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          install(new FactoryModuleBuilder().build(ComplexFactory.class));
        }
      });
      fail("should have failed");
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + Foo.class.getName()
          + " has @AssistedInject constructors, but none of them match the parameters in method "
          + ComplexFactory.class.getName());
    }
  }
  
  public void testNoMatchingConstrucotsBecauseTooLittleParams() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          install(new FactoryModuleBuilder().build(NullFactory.class));
        }
      });
      fail("should have failed");
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), "1) " + Foo.class.getName()
          + " has @AssistedInject constructors, but none of them match the parameters in method "
          + NullFactory.class.getName());
    }
  }

  public static interface ComplexFactory {
    Foo create(String name, int idx, float weight);
  }
  
  public static interface NullFactory {
    Foo create();
  }
  
  public static interface OtherFactory {
    Foo create(String name, int idx);
    Foo create(int idx, String name);
    Foo create(String name);
  }

  
  public static interface Factory {
    Foo create(String name);
    Foo create(String name, int idx);
  }
  
  public static interface BarFactory {
    Bar create(String name);
    Bar create(String name, int idx);
  }
  
  public static interface SimpleFactory {
    Foo create(String name);
  }
  
  public static interface SimpleFactory2 {
    Foo create(String name, int idx);
  }
  
  public static class TooManyMatches extends Foo {
    @AssistedInject TooManyMatches(@Assisted String name, @Assisted int index) {
    }
    
    @AssistedInject TooManyMatches(@Assisted int index, @Assisted String name) {
    }    
  }
  
  public static class Foo implements Bar {
    private String name;
    private Integer index;
    
    Foo() {}
    
    @AssistedInject Foo(@Assisted String name) {
      this.name = name;
      this.index = null;
    }
    
    @AssistedInject Foo(@Assisted String name, @Assisted int index) {
      this.name = name;
      this.index = index;
    }
    
    Foo(String a, String b, String c) {
      
    }
    
    public String getName() { return name; }
    public Integer getIndex() { return index; }
  }
  
  public static interface Bar {
    String getName();
    Integer getIndex();
  }
  
  public void testDependenciesAndOtherAnnotations() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(FamilyFarmFactory.class));
      }
    });
    
    FamilyFarmFactory factory = injector.getInstance(FamilyFarmFactory.class);
    Farm pops = factory.popsFarm("Pop");
    assertEquals("Pop", pops.pop);
    assertEquals(null, pops.mom);
    Farm moms = factory.momsFarm("Mom");
    assertEquals(null, moms.pop);
    assertEquals("Mom", moms.mom);
    Farm momAndPop = factory.momAndPopsFarm("Mom", "Pop");
    assertEquals("Pop", momAndPop.pop);
    assertEquals("Mom", momAndPop.mom);
  }
  

  public static interface FamilyFarmFactory {
    Farm popsFarm(String pop);
    Farm momsFarm(@Assisted("mom") String mom);
    Farm momAndPopsFarm(@Assisted("mom") String mom, @Assisted("pop") String pop);
  }
  
  public static class Farm {
    String pop;
    String mom;
    
    @AssistedInject Farm(@Assisted String pop, Dog dog) {
      this.pop = pop;
    }
    
    @AssistedInject Farm(@Assisted("mom") String mom, @Assisted("pop") String pop, Cow cow, Dog dog) {
      this.pop = pop;
      this.mom = mom;
    }
    
    @AssistedInject Farm(@Assisted("mom") String mom, Cow cow) {
      this.mom = mom;
    }
  }
  
  public static class Cow {}
  public static class Dog {}
  
}