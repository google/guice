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

import static com.google.inject.Scope.*;

import junit.framework.TestCase;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.concurrent.Callable;

/**
 * Performance test.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class SpringTest extends TestCase {

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

    final Container container = new ContainerBuilder()
        .factory(Tee.class, TeeImpl.class)
        .factory(Bar.class, BarImpl.class)
        .factory(Foo.class, Foo.class)
        .constant("i", 5)
        .constant("s", "test")
        .create(false);

    public Foo call() throws Exception {
      return container.inject(Foo.class);
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

  public static void main(String[] args) throws Exception {
    validate(springFactory);
    validate(springFactory);
    validate(juiceFactory);
    validate(juiceFactory);

    int count = 100000;
    for (int i2 = 0; i2 < 10; i2++) {
      long time = System.currentTimeMillis();
      for (int i = 0; i < count; i++)
        springFactory.call();
      time = System.currentTimeMillis() - time;
      System.err.println("Spring: " + count * 1000 / time + "/s");

      time = System.currentTimeMillis();
      for (int i = 0; i < count; i++)
        juiceFactory.call();
      time = System.currentTimeMillis() - time;
      System.err.println("Juice:  " + count * 1000 / time + "/s");
    }
  }

  public static class Foo {

    Bar bar;
    Bar copy;
    String s;
    int i;

    @Inject("i")
    public void setI(int i) {
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

    @Inject("s")
    public void setS(String s) {
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
    public BarImpl(Tee tee, @Inject("i") int i) {
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

  @Scoped(SINGLETON)
  public static class TeeImpl implements Tee {

    final String s;

    @Inject
    public TeeImpl(@Inject("s") String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }
  }
}
