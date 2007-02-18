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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertSame;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.text.DecimalFormat;
import java.util.concurrent.Callable;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A semi-useless microbenchmark. Spring and Guice constuct the same object
 * graph a bunch of times, and we see who can construct the most per second.
 * As of this writing Guice is more than 50X faster. Also useful for comparing 
 * pure Java configuration options.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class PerformanceComparison {

  public static void main(String[] args) throws Exception {
    // Once warm up. Takes lazy loading out of the equation and ensures we
    // created the graphs properly.
    validate(springFactory);
    validate(juiceFactory);
    validate(byHandFactory);

    for (int i2 = 0; i2 < 10; i2++) {
      iterate(springFactory, "Spring:  ");
      iterate(juiceFactory,  "Guice:   ");
      iterate(byHandFactory, "By Hand: ");

      System.err.println();
    }
  }

  static final Callable<Foo> springFactory = new Callable<Foo>() {

    final DefaultListableBeanFactory beanFactory;

    {
      beanFactory = new DefaultListableBeanFactory();

      RootBeanDefinition tee = new RootBeanDefinition(TeeImpl.class, true);
      tee.setLazyInit(true);
      ConstructorArgumentValues teeValues = new ConstructorArgumentValues();
      teeValues.addGenericArgumentValue("test");
      tee.setConstructorArgumentValues(teeValues);

      RootBeanDefinition bar = new RootBeanDefinition(BarImpl.class, false);
      ConstructorArgumentValues barValues = new ConstructorArgumentValues();
      barValues.addGenericArgumentValue(new RuntimeBeanReference("tee"));
      barValues.addGenericArgumentValue(5);
      bar.setConstructorArgumentValues(barValues);

      RootBeanDefinition foo = new RootBeanDefinition(Foo.class, false);
      MutablePropertyValues fooValues = new MutablePropertyValues();
      fooValues.addPropertyValue("i", 5);
      fooValues.addPropertyValue("bar", new RuntimeBeanReference("bar"));
      fooValues.addPropertyValue("copy", new RuntimeBeanReference("bar"));
      fooValues.addPropertyValue("s", "test");
      foo.setPropertyValues(fooValues);

      beanFactory.registerBeanDefinition("foo", foo);
      beanFactory.registerBeanDefinition("bar", bar);
      beanFactory.registerBeanDefinition("tee", tee);
    }

    public Foo call() throws Exception {
      return (Foo) beanFactory.getBean("foo");
    }
  };

  static final Callable<Foo> juiceFactory = new Callable<Foo>() {
    final Locator<Foo> fooLocator;
    {
      ContainerBuilder builder = new ContainerBuilder();

      builder.install(new AbstractModule() {
        protected void configure() {
          bind(Tee.class).to(TeeImpl.class);
          bind(Bar.class).to(BarImpl.class);
          bind(Foo.class);
          bindConstant(I.class).to(5);
          bindConstant(S.class).to("test");
        }
      });

      try {
        fooLocator = builder.create().getLocator(Foo.class);
      } catch (CreationException e) {
        throw new RuntimeException(e);
      }
    }

    public Foo call() throws Exception {
      return fooLocator.get();
    }
  };

  static final Callable<Foo> byHandFactory = new Callable<Foo>() {
    final Tee tee = new TeeImpl("test");
    public Foo call() throws Exception {
      Foo foo = new Foo();
      foo.setI(5);
      foo.setS("test");
      Bar bar = new BarImpl(tee, 5);
      Bar copy = new BarImpl(tee, 5);
      foo.setBar(bar);
      foo.setCopy(copy);
      return foo;
    }
  };

  static void validate(Callable<Foo> t) throws Exception {
    Foo foo = t.call();
    assertEquals(5, foo.i);
    assertEquals("test", foo.s);
    assertSame(foo.bar.getTee(), foo.copy.getTee());
    assertEquals(5, foo.bar.getI());
    assertEquals("test", foo.bar.getTee().getS());
  }

  static final DecimalFormat format = new DecimalFormat();

  static void iterate(Callable<Foo> callable, String label) throws Exception {
    int count = 100000;
    long time = System.currentTimeMillis();
    for (int i = 0; i < count; i++) {
      callable.call();
    }
    time = System.currentTimeMillis() - time;
    System.err.println(label
        + format.format(count * 1000 / time) + " creations/s");
  }

  public static class Foo {

    Bar bar;
    Bar copy;
    String s;
    int i;

    @Inject
    public void setI(@I int i) {
      this.i = i;
    }

    @Inject
    public void setBar(Bar bar) {
      this.bar = bar;
    }

    @Inject
    public void setCopy(Bar copy) {
      this.copy = copy;
    }

    @Inject
    public void setS(@S String s) {
      this.s = s;
    }
  }

  interface Bar {

    Tee getTee();
    int getI();
  }

  public static class BarImpl implements Bar {

    final int i;
    final Tee tee;

    @Inject
    public BarImpl(Tee tee, @I int i) {
      this.tee = tee;
      this.i = i;
    }

    public Tee getTee() {
      return tee;
    }

    public int getI() {
      return i;
    }
  }

  interface Tee {

    String getS();
  }

  @ContainerScoped
  public static class TeeImpl implements Tee {

    final String s;

    @Inject
    public TeeImpl(@S String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }
  }

  @Retention(RUNTIME)
  @Binder @interface I {}

  @Retention(RUNTIME)
  @Binder @interface S {}
}
