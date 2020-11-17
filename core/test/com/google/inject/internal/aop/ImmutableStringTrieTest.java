/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.inject.internal.aop;

import static java.util.Arrays.stream;
import static java.util.Collections.sort;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import junit.framework.TestCase;

/**
 * Tests for {@link ImmutableStringTrie}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class ImmutableStringTrieTest extends TestCase {

  public void testSingletonTrie() {
    ToIntFunction<String> trie = ImmutableStringTrie.buildTrie(ImmutableSet.of("testKey"));
    assertThat(trie.applyAsInt("testKey"), is(0));
  }

  public void testMethodStrings() {
    List<String> table =
        stream(Binder.class.getDeclaredMethods()).map(Method::toString).collect(toList());

    sort(table);

    ToIntFunction<String> trie = ImmutableStringTrie.buildTrie(table);

    for (int i = 0; i < table.size(); i++) {
      assertThat(trie.applyAsInt(table.get(i)), is(i));
    }
  }

  private static final int NUM_TEST_STRINGS = 65536;

  private static final int MAX_STRING_LENGTH = 100;

  public void testRandomStrings() {

    Random random = new SecureRandom();
    StringBuilder buf = new StringBuilder();
    Set<String> strings = new TreeSet<>();

    while (strings.size() < NUM_TEST_STRINGS) {
      randomize(random, buf);
      strings.add(buf.toString());
      buf.setLength(0);
    }

    List<String> table = new ArrayList<>(strings); // already sorted

    ToIntFunction<String> trie = ImmutableStringTrie.buildTrie(table);

    for (int i = 0; i < table.size(); i++) {
      assertThat(trie.applyAsInt(table.get(i)), is(i));
    }
  }

  private static void randomize(Random random, StringBuilder buf) {
    int length = random.nextInt(MAX_STRING_LENGTH) + 1;
    while (buf.length() < length) {
      char c = (char) random.nextInt(Character.MAX_VALUE + 1);
      if (!Character.isSurrogate(c)) {
        buf.append(c);
      }
    }
  }
}
