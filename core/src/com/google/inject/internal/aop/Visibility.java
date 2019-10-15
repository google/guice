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
package com.google.inject.internal.aop;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * The required visibility of a user's class from a Guice-generated class. Visibility of
 * package-private members depends on the loading classloader: only if two classes were loaded by
 * the same classloader can they see each other's package-private members. We need to be careful
 * when choosing which classloader to use for generated classes.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public enum Visibility {

  /**
   * Indicates Guice-generated classes only call or override public members of the target class.
   * They may be loaded by a different classloader to the target class.
   */
  PUBLIC {
    @Override
    public Visibility and(Visibility that) {
      return that;
    }
  },

  /**
   * Indicates Guice-generated classes call or override at least one package-private member. They
   * must be loaded in the same classloader as the target class.
   */
  SAME_PACKAGE {
    @Override
    public Visibility and(Visibility that) {
      return this;
    }
  };

  public static Visibility forMember(Member member) {
    if ((member.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) == 0) {
      return SAME_PACKAGE;
    }

    Class<?>[] parameterTypes;
    if (member instanceof Constructor) {
      parameterTypes = ((Constructor<?>) member).getParameterTypes();
    } else {
      Method method = (Method) member;
      if (forType(method.getReturnType()) == SAME_PACKAGE) {
        return SAME_PACKAGE;
      }
      parameterTypes = method.getParameterTypes();
    }

    for (Class<?> type : parameterTypes) {
      if (forType(type) == SAME_PACKAGE) {
        return SAME_PACKAGE;
      }
    }

    return PUBLIC;
  }

  public static Visibility forType(Class<?> type) {
    return (type.getModifiers() & (Modifier.PROTECTED | Modifier.PUBLIC)) != 0
        ? PUBLIC
        : SAME_PACKAGE;
  }

  public abstract Visibility and(Visibility that);
}
