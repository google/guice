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


package com.google.inject.internal;

import java.util.Iterator;
import java.util.Arrays;
import java.util.Collections;
import java.util.NoSuchElementException;

/**
 * A tiny subset of handy methods copied from the Google Collections library.
 * We should consider adding that .jar to our build dependencies so this class
 * is no longer necessary.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class GoogleCollections {
  private GoogleCollections() {}

  public static <T> Iterable<T> concat(
      final Iterable<? extends T> a, final Iterable<? extends T> b) {
    return new Iterable<T>() {
      public Iterator<T> iterator() {
        return concat(Arrays.asList(a.iterator(), b.iterator()).iterator());
      }
    };
  }

  public static <T> Iterator<T> concat(
      final Iterator<Iterator<? extends T>> inputs) {
    return new Iterator<T>() {
      Iterator<? extends T> current = Collections.<T>emptySet().iterator();

      public boolean hasNext() {
        while (!current.hasNext() && inputs.hasNext()) {
          current = inputs.next();
        }
        return current.hasNext();
      }
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return current.next();
      }
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
