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

import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.ILOAD;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Support code for generating enhancer/fast-class glue.
 *
 * <p>Each glue class has a trampoline that accepts an index, context object, and argument array:
 *
 * <pre>
 * public static Object GUICE$TRAMPOLINE(int index, Object context, Object[] args) {
 *   switch (index) {
 *     case 0: {
 *       return ...;
 *     }
 *     case 1: {
 *       return ...;
 *     }
 *   }
 *   return null;
 * }
 * </pre>
 *
 * Each indexed statement in the trampoline invokes a constructor or method, returning the result.
 * The expected context object depends on the statement; it could be the invocation target, some
 * additional constructor context, or it may be unused. Arguments are unpacked from the array onto
 * the call stack, unboxing or casting them as necessary. Primitive results are autoboxed before
 * being returned.
 *
 * <p>Where possible the trampoline is converted into a lookup {@link Function} mapping an integer
 * to an invoker function, each invoker represented as a {@link BiFunction} that accepts a context
 * object plus argument array and returns the result. These functional interfaces are used to avoid
 * introducing a dependency from the glue class to Guice specific types. This means the glue class
 * can be loaded anywhere that can see the host class, it doesn't need access to Guice's own {@link
 * ClassLoader}. (In other words it removes any need for bridge {@link ClassLoader}s.)
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
abstract class AbstractGlueGenerator {

  protected static final String GENERATED_SOURCE = "<generated>";

  protected static final String TRAMPOLINE_NAME = "GUICE$TRAMPOLINE";

  /**
   * The trampoline method takes an index, along with a context object and an array of argument
   * objects, and invokes the appropriate constructor/method returning the result as an object.
   */
  protected static final String TRAMPOLINE_DESCRIPTOR =
      "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";

  protected final Class<?> hostClass;

  protected final String hostName;

  protected final String proxyName;

  private static final AtomicInteger COUNTER = new AtomicInteger();

  protected AbstractGlueGenerator(Class<?> hostClass, String marker) {
    this.hostClass = hostClass;
    this.hostName = Type.getInternalName(hostClass);
    this.proxyName = proxyName(hostName, marker, hashCode());
  }

  /** Generates a unique name based on the original class name and marker. */
  private static String proxyName(String hostName, String marker, int hash) {
    int id = ((hash & 0x000FFFFF) | (COUNTER.getAndIncrement() << 20));
    String proxyName = hostName + marker + id;
    if (proxyName.startsWith("java/") && !ClassDefining.hasPackageAccess()) {
      proxyName = '$' + proxyName; // can't define java.* glue in same package
    }
    return proxyName;
  }

  /** Generates the enhancer/fast-class and returns a mapping from signature to invoker. */
  public final Function<String, BiFunction<Object, Object[], Object>> glue(
      NavigableMap<String, Executable> glueMap) {
    final MethodHandle invokerTable;
    try {
      byte[] bytecode = generateGlue(glueMap.values());
      Class<?> glueClass = ClassDefining.define(hostClass, bytecode);
      invokerTable = lookupInvokerTable(glueClass);
    } catch (Throwable e) {
      throw new GlueException("Problem generating " + proxyName, e);
    }

    // build optimized index for these signatures and bind it to the generated invokers
    ToIntFunction<String> signatureTable = ImmutableStringTrie.buildTrie(glueMap.keySet());
    return bindSignaturesToInvokers(signatureTable, invokerTable);
  }

  /** Generates enhancer/fast-class bytecode for the given constructors/methods. */
  protected abstract byte[] generateGlue(Collection<Executable> members);

  /** Lookup the invoker table; this may be represented by a function or a trampoline. */
  protected abstract MethodHandle lookupInvokerTable(Class<?> glueClass) throws Throwable;

  /** Combines the signature and invoker tables into a mapping from signature to invoker. */
  private static Function<String, BiFunction<Object, Object[], Object>> bindSignaturesToInvokers(
      ToIntFunction<String> signatureTable, MethodHandle invokerTable) {

    // single-argument method; the table must be a function from integer index to invoker function
    if (invokerTable.type().parameterCount() == 1) {
      return signature -> {
        try {
          // pass this signature's index into the table function to retrieve the invoker
          return (BiFunction<Object, Object[], Object>)
              invokerTable.invokeExact(signatureTable.applyAsInt(signature));
        } catch (Throwable e) {
          throw asIfUnchecked(e);
        }
      };
    }

    // otherwise must be a trampoline method that takes the index and invoker arguments all at once
    return signature -> {
      // bind the index as soon as we have the signature...
      int index = signatureTable.applyAsInt(signature);
      return (instance, arguments) -> {
        try {
          // ...but delay calling trampoline until invocation time when we have the other arguments
          return invokerTable.invokeExact(index, instance, arguments);
        } catch (Throwable e) {
          throw asIfUnchecked(e);
        }
      };
    };
  }

  /** Generics trick to get compiler to treat given exception as if unchecked (as JVM does). */
  @SuppressWarnings("unchecked")
  private static <E extends Throwable> RuntimeException asIfUnchecked(Throwable e) throws E {
    throw (E) e;
  }

  /**
   * Generate trampoline that takes an index, along with a context object and array of argument
   * objects, and invokes the appropriate constructor/method returning the result as an object.
   */
  protected final void generateTrampoline(ClassWriter cw, Collection<Executable> members) {
    MethodVisitor mv =
        cw.visitMethod(PUBLIC | STATIC, TRAMPOLINE_NAME, TRAMPOLINE_DESCRIPTOR, null, null);
    mv.visitCode();

    Label[] labels = new Label[members.size()];
    Arrays.setAll(labels, i -> new Label());
    Label defaultLabel = new Label();

    mv.visitVarInsn(ILOAD, 0);
    mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

    int labelIndex = 0;
    for (Executable member : members) {
      mv.visitLabel(labels[labelIndex++]);
      mv.visitFrame(F_SAME, 0, null, 0, null);
      if (member instanceof Constructor<?>) {
        generateConstructorInvoker(mv, (Constructor<?>) member);
      } else {
        generateMethodInvoker(mv, (Method) member);
      }
      mv.visitInsn(ARETURN);
    }

    mv.visitLabel(defaultLabel);
    mv.visitFrame(F_SAME, 0, null, 0, null);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ARETURN);

    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  /** Generate invoker that takes a context and an argument array and calls the constructor. */
  protected abstract void generateConstructorInvoker(MethodVisitor mv, Constructor<?> constructor);

  /** Generate invoker that takes an instance and an argument array and calls the method. */
  protected abstract void generateMethodInvoker(MethodVisitor mv, Method method);
}
