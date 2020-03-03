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

import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.inject.internal.aop.ClassDefining.define;
import static com.google.inject.internal.aop.ImmutableStringTrie.buildTrie;
import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.SIPUSH;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Support code for generating enhancer/fast-class glue.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
abstract class AbstractGlueGenerator {

  private static final String GLUE_HANDLE = "GUICE$GLUE";

  private static final MethodType RAW_INVOKER =
      methodType(Object.class, Object.class, Object.class);

  private static final MethodType GLUE_INVOKER =
      methodType(Object.class, Object.class, Object[].class);

  private static final MethodType GLUE_TABLE = methodType(BiFunction.class, int.class);

  private static final Lookup LOOKUP = MethodHandles.lookup();

  private static final AtomicInteger COUNTER = new AtomicInteger();

  protected final Class<?> hostClass;

  protected final String hostName;

  protected final String proxyName;

  protected AbstractGlueGenerator(Class<?> hostClass, String marker) {
    this.hostClass = hostClass;
    this.hostName = Type.getInternalName(hostClass);
    this.proxyName = proxyName(hostName, marker, hashCode());
  }

  /** Generates a unique name based on the original class name and marker. */
  private static String proxyName(String hostName, String marker, int hash) {
    int id = ((hash & 0x000FFFFF) | (COUNTER.getAndIncrement() << 20));
    String proxyName = hostName + marker + id;
    if (proxyName.startsWith("java/")) {
      proxyName = '$' + proxyName; // can't define java.* glue in same package
    }
    return proxyName;
  }

  /** Returns a mapping from signature to generated invoker function. */
  public final Function<String, BiFunction> glue(Map<String, Executable> glueMap) {
    return bindGlue(glueMap.keySet(), generateGlue(glueMap.values()));
  }

  /** Generates the appropriate glue for the given constructors/methods. */
  protected abstract byte[] generateGlue(Collection<Executable> members);

  /** Binds the generated glue into lambda form, mapping signatures to invoker functions. */
  private Function<String, BiFunction> bindGlue(Collection<String> signatures, byte[] bytecode) {

    final MethodHandle glueTable;
    try {
      final Class<?> glueClass = define(hostClass, bytecode);

      // generated glue invoker has signature: (int, Object, Object[]) -> Object
      final MethodHandle glueGetter =
          LOOKUP.findStaticGetter(glueClass, GLUE_HANDLE, MethodHandle.class);

      // create lambda that transforms this to: (int) -> ((Object, Object[]) -> Object)
      final CallSite callSite =
          LambdaMetafactory.metafactory(
              LOOKUP,
              "apply",
              GLUE_TABLE,
              RAW_INVOKER,
              (MethodHandle) glueGetter.invokeExact(),
              GLUE_INVOKER);

      glueTable = callSite.getTarget();
    } catch (Throwable e) {
      throwIfUnchecked(e);
      throw new GlueException(e);
    }

    // combine trie with lambda to get: (String) -> ((Object, Object[]) -> Object)
    final ToIntFunction<String> trie = buildTrie(signatures.toArray(new String[signatures.size()]));
    return signature -> {
      try {
        return (BiFunction) glueTable.invokeExact(trie.applyAsInt(signature));
      } catch (Throwable e) {
        throwIfUnchecked(e);
        throw new GlueException(e);
      }
    };
  }

  /** Pushes an integer onto the stack, choosing the most efficient opcode. */
  protected static void pushInteger(MethodVisitor methodVisitor, int value) {
    if (value < -1) {
      methodVisitor.visitLdcInsn(value);
    } else if (value <= 5) {
      methodVisitor.visitInsn(ICONST_0 + value);
    } else if (value <= Byte.MAX_VALUE) {
      methodVisitor.visitIntInsn(BIPUSH, value);
    } else if (value <= Short.MAX_VALUE) {
      methodVisitor.visitIntInsn(SIPUSH, value);
    } else {
      methodVisitor.visitLdcInsn(value);
    }
  }

  /** Returns internal names of exceptions declared by the given constructor/method. */
  protected static String[] exceptionNames(Executable member) {
    Class<?>[] exceptionClasses = member.getExceptionTypes();
    int numExceptionNames = exceptionClasses.length;

    String[] exceptionNames = new String[numExceptionNames];
    for (int i = 0; i < numExceptionNames; i++) {
      exceptionNames[i] = Type.getInternalName(exceptionClasses[i]);
    }
    return exceptionNames;
  }

  /** Retrieves the ASM types for parameters of the given constructor/method. */
  protected static Type[] parameterTypes(Executable member) {
    Class<?>[] parameterClasses = member.getParameterTypes();
    int numParameterTypes = parameterClasses.length;

    Type[] parameterTypes = new Type[numParameterTypes];
    for (int i = 0; i < numParameterTypes; i++) {
      parameterTypes[i] = Type.getType(parameterClasses[i]);
    }
    return parameterTypes;
  }

  /** Generates bytecode to box the primitive value on the Java stack. */
  protected static void maybeBoxParameter(MethodVisitor methodVisitor, Type parameterType) {
    int parameterSort = parameterType.getSort();
    if (parameterSort != Type.OBJECT && parameterSort != Type.ARRAY) {
      String boxedClass = boxedClass(parameterSort);
      String boxMethod = "valueOf";
      String boxDescriptor = '(' + parameterType.getDescriptor() + ")L" + boxedClass + ';';
      methodVisitor.visitMethodInsn(INVOKESTATIC, boxedClass, boxMethod, boxDescriptor, false);
    }
  }

  /** Generates bytecode to unbox the boxed value on the Java stack. */
  protected static void maybeUnboxParameter(MethodVisitor methodVisitor, Type parameterType) {
    int parameterSort = parameterType.getSort();
    if (parameterSort != Type.OBJECT && parameterSort != Type.ARRAY) {
      String boxedClass = boxedClass(parameterSort);
      String unboxMethod = parameterType.getClassName() + "Value";
      String unboxDescriptor = "()" + parameterType.getDescriptor();
      methodVisitor.visitTypeInsn(CHECKCAST, boxedClass);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, boxedClass, unboxMethod, unboxDescriptor, false);
    }
  }

  /** Returns the boxed class for the given primitive parameter. */
  private static String boxedClass(int parameterSort) {
    if (parameterSort == Type.BOOLEAN) {
      return "java/lang/Boolean";
    } else if (parameterSort == Type.CHAR) {
      return "java/lang/Character";
    } else {
      return "java/lang/Number";
    }
  }
}
