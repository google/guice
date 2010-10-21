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


package com.google.inject.name;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertEqualWhenReserialized;
import static com.google.inject.Asserts.assertEqualsBothWays;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.util.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class NamesTest extends TestCase {

  @Named("foo") private String foo;
  private Named namedFoo;
  
  protected void setUp() throws Exception {
    super.setUp();
    namedFoo = getClass().getDeclaredField("foo").getAnnotation(Named.class);
  }

  public void testConsistentEqualsAndHashcode() {
    Named actual = Names.named("foo");
    assertEqualsBothWays(namedFoo, actual);
    assertEquals(namedFoo.toString(), actual.toString());
  }

  public void testNamedIsSerializable() throws IOException {
    assertEqualWhenReserialized(Names.named("foo"));
  }

  public void testBindPropertiesUsingProperties() {
    final Properties teams = new Properties();
    teams.setProperty("SanJose", "Sharks");
    teams.setProperty("Edmonton", "Oilers");

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Names.bindProperties(binder(), teams);
      }
    });

    assertEquals("Sharks", injector.getInstance(Key.get(String.class, Names.named("SanJose"))));
    assertEquals("Oilers", injector.getInstance(Key.get(String.class, Names.named("Edmonton"))));
  }

  public void testBindPropertiesUsingMap() {
    final Map<String, String> properties = ImmutableMap.of(
        "SanJose", "Sharks", "Edmonton", "Oilers");

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Names.bindProperties(binder(), properties);
      }
    });

    assertEquals("Sharks", injector.getInstance(Key.get(String.class, Names.named("SanJose"))));
    assertEquals("Oilers", injector.getInstance(Key.get(String.class, Names.named("Edmonton"))));
  }

  public void testBindPropertiesIncludesInheritedProperties() {
    Properties defaults = new Properties();
    defaults.setProperty("Edmonton", "Eskimos");
    defaults.setProperty("Regina", "Pats");

    final Properties teams = new Properties(defaults);
    teams.setProperty("SanJose", "Sharks");
    teams.setProperty("Edmonton", "Oilers");

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Names.bindProperties(binder(), teams);
      }
    });

    assertEquals("Pats", injector.getInstance(Key.get(String.class, Names.named("Regina"))));
    assertEquals("Oilers", injector.getInstance(Key.get(String.class, Names.named("Edmonton"))));
    assertEquals("Sharks", injector.getInstance(Key.get(String.class, Names.named("SanJose"))));

    try {
      injector.getInstance(Key.get(String.class, Names.named("Calgary")));
      fail();
    } catch (RuntimeException expected) {
    }
  }
}
