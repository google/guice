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
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.SIPUSH;

import java.lang.reflect.Executable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Support code for generating enhancer/fast-class glue.
 *
 * <p>This class is not thread-safe; fresh instances should be used when generating {@link #glue}.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
abstract class AbstractGlueGenerator {

  private static final String METAFACTORY_SIGNATURE =
      "(Ljava/lang/invoke/MethodHandles$Lookup;"
          + "Ljava/lang/String;"
          + "Ljava/lang/invoke/MethodType;"
          + "Ljava/lang/invoke/MethodType;"
          + "Ljava/lang/invoke/MethodHandle;"
          + "Ljava/lang/invoke/MethodType;)"
          + "Ljava/lang/invoke/CallSite;";

  private static final Handle METAFACTORY_HANDLE =
      new Handle(
          H_INVOKESTATIC,
          "java/lang/invoke/LambdaMetafactory",
          "metafactory",
          METAFACTORY_SIGNATURE,
          false);

  private static final Type RAW_FUNCTION_SIGNATURE =
      Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;");

  private static final Type RAW_BIFUNCTION_SIGNATURE =
      Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

  private static final String RETURN_FUNCTION = "()Ljava/util/function/Function;";

  private static final String RETURN_BIFUNCTION = "()Ljava/util/function/BiFunction;";

  private static final String GLUE_PREFIX = "GUICE$GLUE$";

  private static final String GLUE_INDEX_METHOD = GLUE_PREFIX + "INDEX";

  private static final ClassValue<Type> PARAMETER_TYPE_CACHE =
      new ClassValue<Type>() {
        @Override
        protected Type computeValue(Class<?> clazz) {
          return Type.getType(clazz);
        }
      };

  private static final AtomicInteger COUNTER = new AtomicInteger();

  protected final ClassWriter classWriter = new ClassWriter(0);

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
    if (proxyName.startsWith("java.")) {
      proxyName = '$' + proxyName; // can't define java.* glue in same package
    }
    return proxyName;
  }

  /** Generates glue methods along with an index of their method references. */
  public final Function<String, ?> glue(Map<String, Executable> glueMap) {

    prepareGlueClass();

    int glueCount = glueMap.size();
    String[] signatures = new String[glueCount];
    Type[] glueTypes = new Type[glueCount];

    int index = 0;
    for (Entry<String, Executable> entry : glueMap.entrySet()) {
      signatures[index] = entry.getKey();
      glueTypes[index] = addGlue(index, GLUE_PREFIX + index, entry.getValue());
      index++;
    }

    addGlueIndex(glueTypes);
    classWriter.visitEnd();

    Object[] glueIndex;
    try {
      // retrieve the indexed method references for the generated glue
      Class<?> glueClass = define(hostClass, classWriter.toByteArray());
      glueIndex = (Object[]) glueClass.getMethod(GLUE_INDEX_METHOD).invoke(null);
    } catch (Exception e) {
      throwIfUnchecked(e);
      throw new GlueException(e);
    }

    ToIntFunction<String> trie = buildTrie(signatures);
    return signature -> glueIndex[trie.applyAsInt(signature)];
  }

  /** Prepares the skeleton of the glue class. */
  protected abstract void prepareGlueClass();

  /** Adds the appropriate invocation glue for the given constructor/method. */
  protected abstract Type addGlue(int index, String glueId, Executable member);

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

  /** Retrieves the ASM representation of the given parameter type. */
  protected static Type getParameterType(Class<?> clazz) {
    return PARAMETER_TYPE_CACHE.get(clazz);
  }

  /** Generates bytecode to box the primitive value on the Java stack. */
  protected static void boxParameter(MethodVisitor methodVisitor, Type parameterType) {

    String boxedClass = boxedClass(parameterType.getSort());
    String boxMethod = "valueOf";
    String boxDescriptor = '(' + parameterType.getInternalName() + ")L" + boxedClass + ';';

    methodVisitor.visitMethodInsn(INVOKESTATIC, boxedClass, boxMethod, boxDescriptor, false);
  }

  /** Generates bytecode to unbox the boxed value on the Java stack. */
  protected static void unboxParameter(MethodVisitor methodVisitor, Type parameterType) {

    String boxedClass = boxedClass(parameterType.getSort());
    String unboxMethod = parameterType.getClassName() + "Value";
    String unboxDescriptor = "()" + parameterType.getInternalName();

    methodVisitor.visitTypeInsn(CHECKCAST, boxedClass);
    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, boxedClass, unboxMethod, unboxDescriptor, false);
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

  /** Adds a static helper that returns indexed method references for the generated glue. */
  private final void addGlueIndex(Type[] glueTypes) {
    MethodVisitor methodVisitor =
        classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
            GLUE_INDEX_METHOD,
            "()[Ljava/lang/Object;",
            null,
            null);

    methodVisitor.visitCode();

    int glueCount = glueTypes.length;
    pushInteger(methodVisitor, glueCount);
    methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    methodVisitor.visitVarInsn(ASTORE, 0);
    methodVisitor.visitInsn(ICONST_0);
    methodVisitor.visitVarInsn(ISTORE, 1);

    for (int index = 0; index < glueCount; index++) {
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);

      String glueDescriptor = glueTypes[index].getDescriptor();
      // choose the most appropriate functional interface based on the number of glue arguments
      boolean singleArgument = glueDescriptor.charAt(glueDescriptor.indexOf(';') + 1) == ')';
      methodVisitor.visitInvokeDynamicInsn(
          "apply",
          singleArgument ? RETURN_FUNCTION : RETURN_BIFUNCTION,
          METAFACTORY_HANDLE,
          new Object[] {
            singleArgument ? RAW_FUNCTION_SIGNATURE : RAW_BIFUNCTION_SIGNATURE,
            new Handle(H_INVOKESTATIC, proxyName, GLUE_PREFIX + index, glueDescriptor, false),
            glueTypes[index]
          });

      methodVisitor.visitInsn(AASTORE);
      methodVisitor.visitIincInsn(1, 1);
    }

    methodVisitor.visitVarInsn(ALOAD, 0);
    methodVisitor.visitInsn(ARETURN);
    methodVisitor.visitMaxs(3, 2);
    methodVisitor.visitEnd();
  }
}
