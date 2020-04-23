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

import static com.google.inject.internal.BytecodeGen.FASTCLASS_BY_GUICE_MARKER;
import static com.google.inject.internal.aop.BytecodeTasks.box;
import static com.google.inject.internal.aop.BytecodeTasks.unpackArguments;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.BiFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Generates fast-classes.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class FastClass extends AbstractGlueGenerator {

  private static final String[] FAST_CLASS_API = {"java/util/function/BiFunction"};

  private static final String RAW_INVOKER_DESCRIPTOR =
      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

  private static final MethodType INT_CONSTRUCTOR_TYPE =
      MethodType.methodType(void.class, int.class);

  private static final MethodType INDEX_TO_INVOKER_METHOD_TYPE =
      MethodType.methodType(BiFunction.class, int.class);

  private static final String OBJECT_ARRAY_TYPE = Type.getInternalName(Object[].class);

  private static final Lookup LOOKUP = MethodHandles.lookup();

  private final boolean hostIsInterface;

  public FastClass(Class<?> hostClass) {
    super(hostClass, FASTCLASS_BY_GUICE_MARKER);
    hostIsInterface = hostClass.isInterface();
  }

  @Override
  protected byte[] generateGlue(Collection<Executable> members) {
    ClassWriter cw = new ClassWriter(COMPUTE_MAXS);
    MethodVisitor mv;

    cw.visit(V1_8, PUBLIC | FINAL | ACC_SUPER, proxyName, null, "java/lang/Object", FAST_CLASS_API);
    cw.visitSource(GENERATED_SOURCE, null);

    cw.visitField(PRIVATE | FINAL, "index", "I", null, null).visitEnd();

    mv = cw.visitMethod(PUBLIC, "<init>", "(I)V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitFieldInsn(PUTFIELD, proxyName, "index", "I");
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    mv = cw.visitMethod(PUBLIC, "apply", RAW_INVOKER_DESCRIPTOR, null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, proxyName, "index", "I");
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitTypeInsn(CHECKCAST, OBJECT_ARRAY_TYPE);
    mv.visitMethodInsn(INVOKESTATIC, proxyName, TRAMPOLINE_NAME, TRAMPOLINE_DESCRIPTOR, false);
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    generateTrampoline(cw, members);

    cw.visitEnd();
    return cw.toByteArray();
  }

  @Override
  protected void generateConstructorInvoker(MethodVisitor mv, Constructor<?> constructor) {
    mv.visitTypeInsn(NEW, hostName);
    mv.visitInsn(DUP);

    unpackArguments(mv, constructor.getParameterTypes());

    mv.visitMethodInsn(
        INVOKESPECIAL, hostName, "<init>", Type.getConstructorDescriptor(constructor), false);
  }

  @Override
  protected void generateMethodInvoker(MethodVisitor mv, Method method) {

    int invokeOpcode;
    if ((method.getModifiers() & STATIC) == 0) {
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(CHECKCAST, hostName);
      invokeOpcode = hostIsInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
    } else {
      invokeOpcode = INVOKESTATIC;
    }

    unpackArguments(mv, method.getParameterTypes());

    mv.visitMethodInsn(
        invokeOpcode,
        hostName,
        method.getName(),
        Type.getMethodDescriptor(method),
        hostIsInterface);

    Class<?> returnType = method.getReturnType();
    if (returnType == void.class) {
      mv.visitInsn(ACONST_NULL);
    } else if (returnType.isPrimitive()) {
      box(mv, Type.getType(returnType));
    }
  }

  @Override
  protected MethodHandle lookupInvokerTable(Class<?> glueClass) throws Throwable {
    return LOOKUP
        .findConstructor(glueClass, INT_CONSTRUCTOR_TYPE)
        .asType(INDEX_TO_INVOKER_METHOD_TYPE);
  }
}
