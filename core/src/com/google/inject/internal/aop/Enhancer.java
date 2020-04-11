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
import static com.google.inject.internal.aop.BytecodeTasks.box;
import static com.google.inject.internal.aop.BytecodeTasks.loadArgument;
import static com.google.inject.internal.aop.BytecodeTasks.packArguments;
import static com.google.inject.internal.aop.BytecodeTasks.pushInteger;
import static com.google.inject.internal.aop.BytecodeTasks.unbox;
import static com.google.inject.internal.aop.BytecodeTasks.unpackArguments;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.V1_8;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

  private static final String HANDLER_TYPE = Type.getInternalName(InvocationHandler.class);

  private static final String HANDLER_ARRAY_TYPE = Type.getInternalName(InvocationHandler[].class);

  private static final String INVOKERS_NAME = "GUICE$INVOKERS";

  private static final String INVOKERS_DESCRIPTOR = "Ljava/lang/invoke/MethodHandle;";

  private static final String CALLBACK_DESCRIPTOR =
      "(Ljava/lang/Object;"
          + "Ljava/lang/reflect/Method;"
          + "[Ljava/lang/Object;)"
          + "Ljava/lang/Object;";

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

  @Override
  protected byte[] generateGlue(Collection<Executable> members) {
    ClassWriter cw = new ClassWriter(COMPUTE_MAXS);
    MethodVisitor mv;

    cw.visit(V1_8, PUBLIC | ACC_SUPER, proxyName, null, hostName, null);

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

    Set<Method> remainingBridgeMethods = new HashSet<>(bridgeDelegates.keySet());

    int methodIndex = 0;
    cw.visitField(PRIVATE | FINAL, HANDLERS_NAME, HANDLERS_DESCRIPTOR, null, null).visitEnd();
    for (Executable member : members) {
      if (member instanceof Constructor<?>) {
        enhanceConstructor(cw, (Constructor<?>) member);
      } else {
        enhanceMethod(cw, (Method) member, methodIndex++);
        remainingBridgeMethods.remove(member);
      }
    }

    // replace any remaining bridge methods with virtual dispatch to their non-bridge targets
    for (Method method : remainingBridgeMethods) {
      Method target = bridgeDelegates.get(method);
      if (target != null) {
        generateVirtualBridge(cw, method, target);
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

  private void enhanceMethod(ClassWriter cw, Method method, int methodIndex) {
    MethodVisitor mv =
        cw.visitMethod(
            PUBLIC,
            method.getName(),
            Type.getMethodDescriptor(method),
            null,
            exceptionNames(method));

    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(DUP);
    mv.visitFieldInsn(GETFIELD, proxyName, HANDLERS_NAME, HANDLERS_DESCRIPTOR);
    pushInteger(mv, methodIndex);
    mv.visitInsn(AALOAD);
    mv.visitInsn(SWAP);
    mv.visitInsn(ACONST_NULL);
    packArguments(mv, method.getParameterTypes());

    mv.visitMethodInsn(INVOKEINTERFACE, HANDLER_TYPE, "invoke", CALLBACK_DESCRIPTOR, true);

    Class<?> returnType = method.getReturnType();
    if (returnType == void.class) {
      mv.visitInsn(RETURN);
    } else if (returnType.isPrimitive()) {
      Type primitiveType = Type.getType(returnType);
      unbox(mv, primitiveType);
      mv.visitInsn(primitiveType.getOpcode(IRETURN));
    } else {
      mv.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType));
      mv.visitInsn(ARETURN);
    }

    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  @Override
  protected void generateConstructorInvoker(MethodVisitor mv, Constructor<?> constructor) {
    String descriptor = Type.getConstructorDescriptor(constructor);
    String enhancedDescriptor = '(' + HANDLERS_DESCRIPTOR + descriptor.substring(1);

    mv.visitTypeInsn(NEW, proxyName);
    mv.visitInsn(DUP);

    mv.visitVarInsn(ALOAD, 1);
    mv.visitTypeInsn(CHECKCAST, HANDLER_ARRAY_TYPE);
    unpackArguments(mv, constructor.getParameterTypes());

    mv.visitMethodInsn(INVOKESPECIAL, proxyName, "<init>", enhancedDescriptor, false);
  }

  @Override
  protected void generateMethodInvoker(MethodVisitor mv, Method method) {
    Method target = bridgeDelegates.getOrDefault(method, method);

    // if this was a bridge method and we know the target then replace superclass delegation
    // with virtual dispatch to avoid skipping other interceptors overriding the target method
    int invokeOpcode = target != method ? INVOKEVIRTUAL : INVOKESPECIAL;

    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ALOAD, 1);
    // JVM seems to prefer different casting when using defineAnonymous vs child-loader
    mv.visitTypeInsn(CHECKCAST, ClassDefining.hasPackageAccess() ? hostName : proxyName);
    unpackArguments(mv, target.getParameterTypes());

    mv.visitMethodInsn(
        invokeOpcode, hostName, method.getName(), Type.getMethodDescriptor(target), false);

    Class<?> returnType = target.getReturnType();
    if (returnType == void.class) {
      mv.visitInsn(ACONST_NULL);
    } else if (returnType.isPrimitive()) {
      box(mv, Type.getType(returnType));
    }
  }

  /** Override the original bridge method and replace it with virtual dispatch to the target. */
  private void generateVirtualBridge(ClassWriter cw, Method bridge, Method target) {
    MethodVisitor mv =
        cw.visitMethod(
            PUBLIC,
            bridge.getName(),
            Type.getMethodDescriptor(bridge),
            null,
            exceptionNames(bridge));

    mv.visitVarInsn(ALOAD, 0);
    mv.visitTypeInsn(CHECKCAST, hostName);
    int slot = 1;

    Class<?>[] bridgeParameterTypes = bridge.getParameterTypes();
    Class<?>[] targetParameterTypes = target.getParameterTypes();

    for (int i = 0, len = targetParameterTypes.length; i < len; i++) {
      Class<?> parameterType = targetParameterTypes[i];
      slot += loadArgument(mv, parameterType, slot);
      if (parameterType != bridgeParameterTypes[i]) {
        // cast incoming argument to the specific type expected by target
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(parameterType));
      }
    }

    mv.visitMethodInsn(
        INVOKEVIRTUAL, hostName, target.getName(), Type.getMethodDescriptor(target), false);

    Type returnType = Type.getType(bridge.getReturnType());
    if (target.getReturnType() != bridge.getReturnType()) {
      // cast return value to the specific type expected by bridge
      mv.visitTypeInsn(CHECKCAST, returnType.getInternalName());
    }
    mv.visitInsn(returnType.getOpcode(IRETURN));

    mv.visitMaxs(0, 0);
    mv.visitEnd();
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
