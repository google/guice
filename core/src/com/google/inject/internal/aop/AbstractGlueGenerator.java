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
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.SIPUSH;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
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
    if (proxyName.startsWith("java/") && !ClassDefining.hasPackageAccess()) {
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

  protected abstract void generateConstructorInvoker(MethodVisitor mv, Constructor<?> constructor);

  protected abstract void generateMethodInvoker(MethodVisitor mv, Method method);

  protected static void packParameters(MethodVisitor mv, Class<?>[] parameterTypes) {
    pushInteger(mv, parameterTypes.length);
    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    int parameterIndex = 0;
    int slot = 1;
    for (Class<?> parameterType : parameterTypes) {
      mv.visitInsn(DUP);
      pushInteger(mv, parameterIndex++);
      slot = loadParameter(mv, parameterType, slot);
      mv.visitInsn(AASTORE);
    }
  }

  protected static void unpackParameters(MethodVisitor mv, Class<?>[] parameterTypes) {
    int parameterIndex = 0;
    for (Class<?> parameterType : parameterTypes) {
      mv.visitVarInsn(ALOAD, 2);
      pushInteger(mv, parameterIndex++);
      mv.visitInsn(AALOAD);
      if (parameterType.isPrimitive()) {
        unbox(mv, Type.getType(parameterType));
      } else {
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(parameterType));
      }
    }
  }

  /** Pushes an integer onto the stack, choosing the most efficient opcode. */
  protected static void pushInteger(MethodVisitor mv, int value) {
    if (value < -1) {
      mv.visitLdcInsn(value);
    } else if (value <= 5) {
      mv.visitInsn(ICONST_0 + value);
    } else if (value <= Byte.MAX_VALUE) {
      mv.visitIntInsn(BIPUSH, value);
    } else if (value <= Short.MAX_VALUE) {
      mv.visitIntInsn(SIPUSH, value);
    } else {
      mv.visitLdcInsn(value);
    }
  }

  protected static int loadParameter(MethodVisitor mv, Class<?> parameterType, int slot) {
    if (parameterType.isPrimitive()) {
      Type primitiveType = Type.getType(parameterType);
      mv.visitVarInsn(primitiveType.getOpcode(ILOAD), slot);
      box(mv, primitiveType);
      return slot + primitiveType.getSize();
    }
    mv.visitVarInsn(ALOAD, slot);
    return slot + 1;
  }

  /** Generates bytecode to box a primitive value on the Java stack. */
  protected static void box(MethodVisitor mv, Type primitiveType) {
    String boxedClass = boxedClass(primitiveType.getSort());
    String boxMethod = "valueOf";
    String boxDescriptor = '(' + primitiveType.getDescriptor() + ")L" + boxedClass + ';';
    mv.visitMethodInsn(INVOKESTATIC, boxedClass, boxMethod, boxDescriptor, false);
  }

  /** Generates bytecode to unbox a boxed value on the Java stack. */
  protected static void unbox(MethodVisitor mv, Type primitiveType) {
    String boxedClass = boxedClass(primitiveType.getSort());
    String unboxMethod = primitiveType.getClassName() + "Value";
    String unboxDescriptor = "()" + primitiveType.getDescriptor();
    mv.visitTypeInsn(CHECKCAST, boxedClass);
    mv.visitMethodInsn(INVOKEVIRTUAL, boxedClass, unboxMethod, unboxDescriptor, false);
  }

  /** Returns the boxed class for the given primitive type. */
  private static String boxedClass(int primitiveSort) {
    if (primitiveSort == Type.BOOLEAN) {
      return "java/lang/Boolean";
    } else if (primitiveSort == Type.CHAR) {
      return "java/lang/Character";
    } else {
      return "java/lang/Number";
    }
  }
}
