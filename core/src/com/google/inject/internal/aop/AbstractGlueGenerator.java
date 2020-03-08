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

import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.SIPUSH;

import java.lang.invoke.MethodHandle;
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
    if (proxyName.startsWith("java/")) {
      proxyName = '$' + proxyName; // can't define java.* glue in same package
    }
    return proxyName;
  }

  /** Generates the enhancer/fast-class and returns a mapping from signature to invoker. */
  public final Function<String, BiFunction> glue(Map<String, Executable> glueMap) {
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
  private static Function<String, BiFunction> bindSignaturesToInvokers(
      ToIntFunction<String> signatureTable, MethodHandle invokerTable) {

    // single-argument method; assume table is a function from integer index to invoker
    if (invokerTable.type().parameterCount() == 1) {
      return signature -> {
        try {
          // pass this signature's index into the table function to retrieve the invoker
          return (BiFunction) invokerTable.invokeExact(signatureTable.applyAsInt(signature));
        } catch (Throwable e) {
          throw asIfUnchecked(e);
        }
      };
    }

    // otherwise must be trampoline that accepts the index with other arguments at invocation time
    return signature -> {
      // bind the index when we have the signature...
      int index = signatureTable.applyAsInt(signature);
      return (instance, arguments) -> {
        try {
          // ...but delay applying it until invocation time
          return invokerTable.invokeExact(index, instance, (Object[]) arguments);
        } catch (Throwable e) {
          throw asIfUnchecked(e);
        }
      };
    };
  }

  @SuppressWarnings("unchecked")
  /** Generics trick to get compiler to treat given exception as if unchecked (as JVM does). */
  private static <E extends Throwable> RuntimeException asIfUnchecked(Throwable e) throws E {
    throw (E) e;
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

  /** Generates bytecode to box a primitive value on the Java stack. */
  protected static void maybeBoxParameter(MethodVisitor methodVisitor, Type parameterType) {
    int parameterSort = parameterType.getSort();
    if (parameterSort != Type.OBJECT && parameterSort != Type.ARRAY) {
      String boxedClass = boxedClass(parameterSort);
      String boxMethod = "valueOf";
      String boxDescriptor = '(' + parameterType.getDescriptor() + ")L" + boxedClass + ';';
      methodVisitor.visitMethodInsn(INVOKESTATIC, boxedClass, boxMethod, boxDescriptor, false);
    }
  }

  /** Generates bytecode to unbox a boxed value on the Java stack. */
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
