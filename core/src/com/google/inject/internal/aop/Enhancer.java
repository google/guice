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

import static com.google.inject.internal.BytecodeGen.ENHANCER_BY_GUICE_MARKER;
import static com.google.inject.internal.aop.BytecodeTasks.loadArgument;
import static com.google.inject.internal.aop.BytecodeTasks.unpackArguments;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Generates enhanced classes.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class Enhancer extends AbstractGlueGenerator {

  private static final String HANDLERS_NAME = "GUICE$HANDLERS";

  private static final String HANDLERS_DESCRIPTOR = "[Ljava/lang/reflect/InvocationHandler;";

  protected static final String INVOKERS_NAME = "GUICE$INVOKERS";

  private static final String INVOKERS_DESCRIPTOR = "Ljava/lang/invoke/MethodHandle;";

  private static final String METAFACTORY_DESCRIPTOR =
      "(Ljava/lang/invoke/MethodHandles$Lookup;"
          + "Ljava/lang/String;"
          + "Ljava/lang/invoke/MethodType;"
          + "Ljava/lang/invoke/MethodType;"
          + "Ljava/lang/invoke/MethodHandle;"
          + "Ljava/lang/invoke/MethodType;)"
          + "Ljava/lang/invoke/CallSite;";

  private static final Type INDEX_TO_INVOKER_METHOD_TYPE =
      Type.getMethodType("(I)Ljava/util/function/BiFunction;");

  private static final Type RAW_INVOKER_METHOD_TYPE =
      Type.getMethodType("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

  private static final Type INVOKER_METHOD_TYPE =
      Type.getMethodType("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

  private final Map<Method, Method> bridgeDelegates;

  public Enhancer(Class<?> hostClass, Map<Method, Method> bridgeDelegates) {
    super(hostClass, ENHANCER_BY_GUICE_MARKER);
    this.bridgeDelegates = bridgeDelegates;
  }

  static {
    if (Enhancer.class.getName().indexOf('/') > 0) {
      System.err.println("anon");
    }
  }

  @Override
  protected byte[] generateGlue(Collection<Executable> members) {
    ClassWriter cw = new ClassWriter(COMPUTE_MAXS);
    MethodVisitor mv;

    cw.visit(V1_8, PUBLIC | FINAL | ACC_SUPER, proxyName, null, hostName, null);

    cw.visitField(PUBLIC | STATIC | FINAL, INVOKERS_NAME, INVOKERS_DESCRIPTOR, null, null)
        .visitEnd();

    Handle trampolineHandle =
        new Handle(H_INVOKESTATIC, proxyName, TRAMPOLINE_NAME, TRAMPOLINE_DESCRIPTOR, false);

    mv = cw.visitMethod(PRIVATE | STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();

    if (ClassDefining.hasPackageAccess()) {
      mv.visitLdcInsn(trampolineHandle);
    } else {
      mv.visitMethodInsn(
          INVOKESTATIC,
          "java/lang/invoke/MethodHandles",
          "lookup",
          "()Ljava/lang/invoke/MethodHandles$Lookup;",
          false);

      mv.visitLdcInsn("apply");
      mv.visitLdcInsn(INDEX_TO_INVOKER_METHOD_TYPE);
      mv.visitLdcInsn(RAW_INVOKER_METHOD_TYPE);
      mv.visitLdcInsn(trampolineHandle);
      mv.visitLdcInsn(INVOKER_METHOD_TYPE);

      mv.visitMethodInsn(
          INVOKESTATIC,
          "java/lang/invoke/LambdaMetafactory",
          "metafactory",
          METAFACTORY_DESCRIPTOR,
          false);

      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/invoke/CallSite",
          "getTarget",
          "()Ljava/lang/invoke/MethodHandle;",
          false);
    }

    mv.visitFieldInsn(PUTSTATIC, proxyName, INVOKERS_NAME, INVOKERS_DESCRIPTOR);

    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    generateTrampoline(cw, members);

    cw.visitField(PRIVATE | FINAL, HANDLERS_NAME, HANDLERS_DESCRIPTOR, null, null).visitEnd();
    for (Executable member : members) {
      if (member instanceof Constructor<?>) {
        enhanceConstructor(cw, (Constructor<?>) member);
      } else {
        enhanceMethod(cw, (Method) member);
      }
    }

    cw.visitEnd();
    return cw.toByteArray();
  }

  private void enhanceConstructor(ClassWriter cw, Constructor<?> constructor) {
    String descriptor = Type.getConstructorDescriptor(constructor);
    String enhancedDescriptor = '(' + HANDLERS_DESCRIPTOR + descriptor.substring(1);

    MethodVisitor mv =
        cw.visitMethod(PUBLIC, "<init>", enhancedDescriptor, null, exceptionNames(constructor));

    mv.visitCode();

    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitFieldInsn(PUTFIELD, proxyName, HANDLERS_NAME, HANDLERS_DESCRIPTOR);

    int slot = 2;
    for (Class<?> parameterType : constructor.getParameterTypes()) {
      slot += loadArgument(mv, parameterType, slot);
    }

    mv.visitMethodInsn(INVOKESPECIAL, hostName, "<init>", descriptor, false);

    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private void enhanceMethod(ClassWriter cw, Method method) {
    // TODO
  }

  @Override
  protected void generateConstructorInvoker(MethodVisitor mv, Constructor<?> constructor) {
    mv.visitVarInsn(ALOAD, 1);
    unpackArguments(mv, constructor.getParameterTypes());
    // INVOKE!
  }

  @Override
  protected void generateMethodInvoker(MethodVisitor mv, Method method) {
    mv.visitVarInsn(ALOAD, 1);
    unpackArguments(mv, method.getParameterTypes());
    // INVOKE!
  }

  @Override
  protected MethodHandle lookupInvokerTable(Class<?> glueClass) throws Throwable {
    return (MethodHandle) glueClass.getField(INVOKERS_NAME).get(null);
  }

  /** Returns internal names of exceptions declared by the given constructor/method. */
  private static String[] exceptionNames(Executable member) {
    Class<?>[] exceptionClasses = member.getExceptionTypes();
    String[] exceptionNames = new String[exceptionClasses.length];
    Arrays.setAll(exceptionNames, i -> Type.getInternalName(exceptionClasses[i]));
    return exceptionNames;
  }
}
