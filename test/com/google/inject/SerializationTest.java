/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.io.*;
import java.util.AbstractList;
import java.util.List;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class SerializationTest extends TestCase {

  public void testAbstractModuleIsSerializable() throws IOException {
    reserialize(new MyAbstractModule());
  }
  static class MyAbstractModule extends AbstractModule implements Serializable {
    protected void configure() {}
  }

  public void testKeyIsSerializable() throws IOException {
    assertEqualWhenReserialized(Key.get(B.class));
    assertEqualWhenReserialized(Key.get(B.class, Names.named("bee")));
    assertEqualWhenReserialized(Key.get(B.class, Named.class));
    assertEqualWhenReserialized(Key.get(new TypeLiteral<List<B>>() {}));
  }

  public void testSingletonScopeIsNotSerializable() throws IOException {
    assertNotSerializable(Scopes.SINGLETON);
  }

  public void testTypeLiteralIsSerializable() throws IOException {
    assertEqualWhenReserialized(new TypeLiteral<List<B>>() {});
  }

  public void testCreationExceptionIsSerializable() throws IOException {
    assertEqualWhenReserialized(createCreationException());
  }

  private CreationException createCreationException() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(List.class);
        }
      });
      throw new AssertionFailedError();
    } catch (CreationException e) {
      return e;
    }
  }

  public void testConfigurationExceptionIsSerializable() throws IOException {
    assertSimilarWhenReserialized(createConfigurationException());
  }

  private ConfigurationException createConfigurationException() {
    try {
      Guice.createInjector().getInstance(List.class);
      throw new AssertionFailedError();
    } catch (ConfigurationException e) {
      return e;
    }
  }
  
  public void testProvisionExceptionIsSerializable() throws IOException {
    assertSimilarWhenReserialized(createProvisionException());
  }

  private ProvisionException createProvisionException() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(B.class).toProvider(ProviderThatThrows.class);
        }
      }).getInstance(A.class);
      throw new AssertionFailedError();
    } catch (ProvisionException e) {
      return e;
    }
  }

  public void testMatchersAreSerializable() throws IOException {
    assertSimilarWhenReserialized(Matchers.annotatedWith(Names.named("foo")));
    assertSimilarWhenReserialized(Matchers.annotatedWith(Named.class));
    assertSimilarWhenReserialized(Matchers.any());
    assertSimilarWhenReserialized(Matchers.identicalTo(null));
    assertSimilarWhenReserialized(Matchers.inPackage(String.class.getPackage()));
    assertSimilarWhenReserialized(Matchers.not(Matchers.any()));
    assertSimilarWhenReserialized(Matchers.only("foo"));
    assertSimilarWhenReserialized(Matchers.returns(Matchers.any()));
    assertSimilarWhenReserialized(Matchers.subclassesOf(AbstractList.class));
  }

  public void testInjectorIsNotSerializable() throws IOException {
    assertNotSerializable(Guice.createInjector());
  }

  static class ProviderThatThrows implements Provider<B> {
    public B get() {
      throw new IllegalArgumentException();
    }
  }

  static class A {
    @Inject B b;
  }

  static class B {}

  private void assertNotSerializable(Object object) throws IOException {
    try {
      reserialize(object);
      fail();
    } catch (NotSerializableException expected) {
    }
  }

  public void assertEqualWhenReserialized(Object object) throws IOException {
    Object reserialized = reserialize(object);
    assertEquals(object, reserialized);
    assertEquals(object.hashCode(), reserialized.hashCode());
  }

  /**
   * For objects that don't define equals(), we test that they produce the same
   * toString() output.
   */
  public void assertSimilarWhenReserialized(Object object) throws IOException {
    Object reserialized = reserialize(object);
    assertEquals(object.toString(), reserialized.toString());
  }

  private static Object reserialize(Object object) throws IOException {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      new ObjectOutputStream(out).writeObject(object);
      ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
      return new ObjectInputStream(in).readObject();
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
