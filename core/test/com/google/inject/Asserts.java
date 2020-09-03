/*
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

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;
import static com.google.inject.internal.InternalFlags.getIncludeStackTraceOption;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.testing.GcFinalization;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import junit.framework.Assert;

/** @author jessewilson@google.com (Jesse Wilson) */
public class Asserts {

  private Asserts() {}

  /**
   * Returns the String that would appear in an error message for this chain of classes as modules.
   */
  public static String asModuleChain(Class<?>... classes) {
    return Joiner.on(" -> ")
        .appendTo(
            new StringBuilder(" (via modules: "),
            Iterables.transform(ImmutableList.copyOf(classes), Class::getName))
        .append(")")
        .toString();
  }

  /**
   * Returns the source file appears in error messages based on {@link
   * #getIncludeStackTraceOption()} value.
   */
  public static String getDeclaringSourcePart(Class<?> clazz) {
    if (getIncludeStackTraceOption() == IncludeStackTraceOption.OFF) {
      return ".configure(Unknown Source";
    }
    return ".configure(" + clazz.getSimpleName() + ".java:";
  }

  /**
   * Returns true if {@link #getIncludeStackTraceOption()} returns {@link
   * IncludeStackTraceOption#OFF}.
   */
  public static boolean isIncludeStackTraceOff() {
    return getIncludeStackTraceOption() == IncludeStackTraceOption.OFF;
  }

  /**
   * Returns true if {@link #getIncludeStackTraceOption()} returns {@link
   * IncludeStackTraceOption#COMPLETE}.
   */
  public static boolean isIncludeStackTraceComplete() {
    return getIncludeStackTraceOption() == IncludeStackTraceOption.COMPLETE;
  }

  /**
   * Fails unless {@code expected.equals(actual)}, {@code actual.equals(expected)} and their hash
   * codes are equal. This is useful for testing the equals method itself.
   */
  public static void assertEqualsBothWays(Object expected, Object actual) {
    assertNotNull(expected);
    assertNotNull(actual);
    assertEquals("expected.equals(actual)", actual, expected);
    assertEquals("actual.equals(expected)", expected, actual);
    assertEquals("hashCode", expected.hashCode(), actual.hashCode());
  }

  /** Fails unless {@code text} includes all {@code substrings}, in order, no duplicates */
  public static void assertContains(String text, String... substrings) {
    assertContains(text, false, substrings);
  }

  /**
   * Fails unless {@code text} includes all {@code substrings}, in order, and optionally {@code
   * allowDuplicates}.
   */
  public static void assertContains(String text, boolean allowDuplicates, String... substrings) {
    /*if[NO_AOP]
    // when we strip out bytecode manipulation, we lose the ability to generate some source lines.
    if (text.contains("(Unknown Source)")) {
      return;
    }
    end[NO_AOP]*/

    int startingFrom = 0;
    for (String substring : substrings) {
      int index = text.indexOf(substring, startingFrom);
      assertTrue(
          String.format("Expected \"%s\" to contain substring \"%s\"", text, substring),
          index >= startingFrom);
      startingFrom = index + substring.length();
    }

    if (!allowDuplicates) {
      String lastSubstring = substrings[substrings.length - 1];
      assertTrue(
          String.format(
              "Expected \"%s\" to contain substring \"%s\" only once),", text, lastSubstring),
          text.indexOf(lastSubstring, startingFrom) == -1);
    }
  }

  /** Fails unless {@code object} doesn't equal itself when reserialized. */
  public static void assertEqualWhenReserialized(Object object) throws IOException {
    Object reserialized = reserialize(object);
    assertEquals(object, reserialized);
    assertEquals(object.hashCode(), reserialized.hashCode());
  }

  /** Fails unless {@code object} has the same toString value when reserialized. */
  public static void assertSimilarWhenReserialized(Object object) throws IOException {
    Object reserialized = reserialize(object);
    assertEquals(object.toString(), reserialized.toString());
  }

  public static <E> E reserialize(E original) throws IOException {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      new ObjectOutputStream(out).writeObject(original);
      ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
      @SuppressWarnings("unchecked") // the reserialized type is assignable
      E reserialized = (E) new ObjectInputStream(in).readObject();
      return reserialized;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertNotSerializable(Object object) throws IOException {
    try {
      reserialize(object);
      Assert.fail();
    } catch (NotSerializableException expected) {
    }
  }

  public static void awaitFullGc() {
    // GcFinalization *should* do it, but doesn't work well in practice...
    // so we put a second latch and wait for a ReferenceQueue to tell us.
    ReferenceQueue<Object> queue = new ReferenceQueue<>();
    WeakReference<Object> ref = new WeakReference<>(new Object(), queue);
    GcFinalization.awaitFullGc();
    try {
      assertSame("queue didn't return ref in time", ref, queue.remove(5000));
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static void awaitClear(WeakReference<?> ref) {
    // GcFinalization *should* do it, but doesn't work well in practice...
    // so we put a second latch and wait for a ReferenceQueue to tell us.
    Object data = ref.get();
    ReferenceQueue<Object> queue = null;
    WeakReference<Object> extraRef = null;
    if (data != null) {
      queue = new ReferenceQueue<>();
      extraRef = new WeakReference<>(data, queue);
      data = null;
    }
    GcFinalization.awaitClear(ref);
    if (queue != null) {
      try {
        assertSame("queue didn't return ref in time", extraRef, queue.remove(5000));
      } catch (IllegalArgumentException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Returns the URLs in the system class path. */
  // TODO(user): Use a common API once that's available.
  public static URL[] getClassPathUrls() {
    if (Asserts.class.getClassLoader() instanceof URLClassLoader) {
      return ((URLClassLoader) Asserts.class.getClassLoader()).getURLs();
    }
    ImmutableList.Builder<URL> urls = ImmutableList.builder();
    for (String entry : Splitter.on(PATH_SEPARATOR.value()).split(JAVA_CLASS_PATH.value())) {
      try {
        try {
          urls.add(new File(entry).toURI().toURL());
        } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
          urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
        }
      } catch (MalformedURLException e) {
        AssertionError error = new AssertionError("malformed class path entry: " + entry);
        error.initCause(e);
        throw error;
      }
    }
    return urls.build().toArray(new URL[0]);
  }
}
