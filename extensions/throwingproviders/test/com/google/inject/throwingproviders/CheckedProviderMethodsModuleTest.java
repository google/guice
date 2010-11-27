/**
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.throwingproviders;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Exposed;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.BindException;
import java.rmi.RemoteException;

/**
 * Test methods for {@link CheckedProviderMethodsModule}.
 */
public class CheckedProviderMethodsModuleTest extends TestCase {

  private final TypeLiteral<RpcProvider<String>> rpcProviderOfString
      = new TypeLiteral<RpcProvider<String>>() { };
  private final TypeLiteral<RpcProvider<Integer>> rpcProviderOfInteger
      = new TypeLiteral<RpcProvider<Integer>>() { };
  private final TypeLiteral<RpcProvider<Long>> rpcProviderOfLong
      = new TypeLiteral<RpcProvider<Long>>() { };
  private final TypeLiteral<RpcProvider<Float>> rpcProviderOfFloat
      = new TypeLiteral<RpcProvider<Float>>() { };
  private final TypeLiteral<RpcProvider<Pair<Double, String>>> rpcProviderOfPair
      = new TypeLiteral<RpcProvider<Pair<Double, String>>>() { };

  private final TestScope testScope = new TestScope();

  interface RpcProvider<T> extends CheckedProvider<T> {
    T get() throws RemoteException, BindException;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface TestAnnotation {
  }

  class TestModule extends AbstractModule {

    private int nextIntToReturn = 100;

    @Override
    protected void configure() {
      bindScope(TestScope.Scoped.class, testScope);
      install(ThrowingProviderBinder.forModule(this));
      install(new TestPrivateModule());
    }

    @CheckedProvides(RpcProvider.class)
    String getSomeStringFromServer() {
      return "Works";
    }

    @CheckedProvides(RpcProvider.class) @TestScope.Scoped
    int getSomeIntegerFromServer() {
      return nextIntToReturn;
    }

    @CheckedProvides(RpcProvider.class) @TestAnnotation
    long getSomeLongFromServer() {
      return 0xffL;
    }

    @Provides
    double getSomeDouble() {
      return 2.0d;
    }

    @CheckedProvides(RpcProvider.class)
    Pair<Double, String> getSomePair(Double input) {
      return new Pair<Double, String>(input * 2, "foo");
    }

    @CheckedProvides(RpcProvider.class)
    float getFloat() throws BindException {
      throw new BindException("foo");
    }

    void setNextIntToReturn(int next) {
      nextIntToReturn = next;
    }
  }

  class TestPrivateModule extends PrivateModule {

    @Override
    protected void configure() {
      install(ThrowingProviderBinder.forModule(this));
    }

    @CheckedProvides(RpcProvider.class) @Named("fruit") @Exposed
    String provideApples() {
      return "apple";
    }
  }
  

  public void testNoAnnotationNoScope() throws BindException, RemoteException {
    Injector injector = Guice.createInjector(new TestModule());
    RpcProvider<String> provider = injector
        .getInstance(Key.get(rpcProviderOfString));
    assertEquals("Works", provider.get());
  }

  public void testWithScope() throws BindException, RemoteException {
    TestModule testModule = new TestModule();
    Injector injector = Guice.createInjector(testModule);
    RpcProvider<Integer> provider = injector
        .getInstance(Key.get(rpcProviderOfInteger));

    assertEquals((Integer)100, provider.get());
    testModule.setNextIntToReturn(120);
    assertEquals((Integer)100, provider.get());
    testScope.beginNewScope();
    assertEquals((Integer)120, provider.get());
  }

  public void testWithAnnotation() throws BindException, RemoteException {
    TestModule testModule = new TestModule();
    Injector injector = Guice.createInjector(testModule);
    RpcProvider<Long> provider = injector
        .getInstance(Key.get(rpcProviderOfLong, TestAnnotation.class));
    assertEquals((Long)0xffL, provider.get());
  }

  public void testWithInjectedParameters() throws BindException, RemoteException {
    TestModule testModule = new TestModule();
    Injector injector = Guice.createInjector(testModule);
    RpcProvider<Pair<Double, String>> provider = injector
        .getInstance(Key.get(rpcProviderOfPair));
    Pair<Double, String> pair = provider.get();
    assertEquals(pair.first, 4.0d);
  }

  public void testWithThrownException() {
    TestModule testModule = new TestModule();
    Injector injector = Guice.createInjector(testModule);
    RpcProvider<Float> provider = injector
        .getInstance(Key.get(rpcProviderOfFloat));
    try {
      provider.get();
      fail();
    } catch (RemoteException e) {
      fail();
    } catch (BindException e) {
      // good
    }
  }

  public void testExposedMethod() throws BindException, RemoteException {
    TestModule testModule = new TestModule();
    Injector injector = Guice.createInjector(testModule);
    RpcProvider<String> provider = injector
        .getInstance(Key.get(rpcProviderOfString, Names.named("fruit")));
    assertEquals("apple", provider.get());

  }
  
  private static class Pair<A, B> {
	A first;
	B second;
	
	Pair(A a, B b) {
	 this.first= a;
	 this.second = b;
	}
  }
}