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
import static org.objectweb.asm.Opcodes.H_NEWINVOKESPECIAL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collection;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Generates fast-classes.
 *
 * <p>Each fast-class has a single constructor that takes an index. It also has an instance method
 * that takes a context object and an array of argument objects which it combines with the index to
 * call the shared static trampoline. Each fast-class instance therefore acts like a bound invoker
 * to the appropriate constructor or method of the host class.
 *
 * <p>A handle to the fast-class constructor is used as the invoker table, mapping index to invoker.
 *
 * <p>Fast-classes have the following pseudo-Java structure:
 *
 * <pre>
 * public final class HostClass$$FastClassByGuice
 *   implements BiFunction // each fast-class instance represents a bound invoker
 * {
 *   private final int index; // the bound trampoline index
 *
 *   public HostClass$$FastClassByGuice(int index) {
 *     this.index = index;
 *   }
 *
 *   public Object apply(Object context, Object args) {
 *     return GUICE$TRAMPOLINE(index, context, (Object[]) args);
 *   }
 *
 *   public static Object GUICE$TRAMPOLINE(int index, Object context, Object[] args) {
 *     switch (index) {
 *       case 0: {
 *         return new HostClass(...);
 *       }
 *       case 1: {
 *         return ((HostClass) context).instanceMethod(...);
 *       }
 *       case 2: {
 *         return HostClass.staticMethod(...);
 *       }
 *     }
 *     return null;
 *   }
 * }
 * </pre>
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class FastClass extends AbstractGlueGenerator {

  private static final String[] FAST_CLASS_API = {"java/util/function/BiFunction"};

  private static final String INVOKERS_NAME = "GUICE$INVOKERS";

  private static final String INVOKERS_DESCRIPTOR = "Ljava/lang/invoke/MethodHandle;";

  private static final Type INDEX_TO_INVOKER_METHOD_TYPE =
      Type.getMethodType("(I)Ljava/util/function/BiFunction;");

  private static final String RAW_INVOKER_DESCRIPTOR =
      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

  private static final String OBJECT_ARRAY_TYPE = Type.getInternalName(Object[].class);

  private final boolean hostIsInterface;

  FastClass(Class<?> hostClass) {
    super(hostClass, FASTCLASS_BY_GUICE_MARKER);
    hostIsInterface = hostClass.isInterface();
  }

  @Override
  protected byte[] generateGlue(Collection<Executable> members) {
    ClassWriter cw = new ClassWriter(COMPUTE_MAXS);
    MethodVisitor mv;

    // target Java8 because that's all we need for the generated trampoline code
    cw.visit(V1_8, PUBLIC | FINAL | ACC_SUPER, proxyName, null, "java/lang/Object", FAST_CLASS_API);
    cw.visitSource(GENERATED_SOURCE, null);

    // this shared field contains the constructor handle adapted to look like an invoker table
    cw.visitField(PUBLIC | STATIC | FINAL, INVOKERS_NAME, INVOKERS_DESCRIPTOR, null, null)
        .visitEnd();

    setupInvokerTable(cw);

    cw.visitField(PRIVATE | FINAL, "index", "I", null, null).visitEnd();

    // fast-class constructor that takes an index and binds it
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

    // fast-class invoker function that takes a context object and argument array
    mv = cw.visitMethod(PUBLIC, "apply", RAW_INVOKER_DESCRIPTOR, null, null);
    mv.visitCode();
    // combine bound index with context object and argument array
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, proxyName, "index", "I");
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitTypeInsn(CHECKCAST, OBJECT_ARRAY_TYPE);
    // call into the shared trampoline
    mv.visitMethodInsn(INVOKESTATIC, proxyName, TRAMPOLINE_NAME, TRAMPOLINE_DESCRIPTOR, false);
    mv.visitInsn(ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    generateTrampoline(cw, members);

    cw.visitEnd();
    return cw.toByteArray();
  }

  /** Generate static initializer to setup invoker table based on the fast-class constructor. */
  private void setupInvokerTable(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(PRIVATE | STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();

    Handle constructorHandle = new Handle(H_NEWINVOKESPECIAL, proxyName, "<init>", "(I)V", false);

    mv.visitLdcInsn(constructorHandle);

    // adapt constructor handle to make it look like an invoker table (int -> BiFunction)
    mv.visitLdcInsn(INDEX_TO_INVOKER_METHOD_TYPE);
    mv.visitMethodInsn(
        INVOKEVIRTUAL,
        "java/lang/invoke/MethodHandle",
        "asType",
        "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
        false);

    mv.visitFieldInsn(PUTSTATIC, proxyName, INVOKERS_NAME, INVOKERS_DESCRIPTOR);

    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  @Override
  protected void generateConstructorInvoker(MethodVisitor mv, Constructor<?> constructor) {
    mv.visitTypeInsn(NEW, hostName);
    mv.visitInsn(DUP);

    // fast-class constructor invokers don't use the context object

    unpackArguments(mv, constructor.getParameterTypes());

    mv.visitMethodInsn(
        INVOKESPECIAL, hostName, "<init>", Type.getConstructorDescriptor(constructor), false);
  }

  @Override
  protected void generateMethodInvoker(MethodVisitor mv, Method method) {

    int invokeOpcode;
    if ((method.getModifiers() & STATIC) == 0) {
      // context object is the instance whose method we want to call
      mv.visitVarInsn(ALOAD, 1);
      mv.visitTypeInsn(CHECKCAST, hostName);
      invokeOpcode = hostIsInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
    } else {
      // fast-class static method invokers don't use the context object
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
    return (MethodHandle) glueClass.getField(INVOKERS_NAME).get(null);
  }
}
