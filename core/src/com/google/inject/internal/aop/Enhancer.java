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
import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.NATIVE;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.SYNCHRONIZED;
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
 * <p>Each enhancer has the same number of constructors as the class it enhances, but each
 * constructor takes an additional handler array before the rest of the expected arguments.
 *
 * <p>Enhanced methods are overridden to call the handler with the same index as the method. The
 * handler delegates to the interceptor stack. Once the last interceptor returns the handler will
 * call back into the trampoline with the method index, which invokes the superclass method.
 *
 * <p>The trampoline also provides access to constructor invokers that take a context object (the
 * handler array) with an argument array and invokes the appropriate enhanced constructor. These
 * invokers are used in the proxy factory to create enhanced instances.
 *
 * <p>Enhanced classes have the following pseudo-Java structure:
 *
 * <pre>
 * public class HostClass$$EnhancerByGuice
 *   extends HostClass
 * {
 *   // InterceptorStackCallbacks, one per enhanced method
 *   private final InvocationHandler[] GUICE$HANDLERS;
 *
 *   public HostClass$$EnhancerByGuice(InvocationHandler[] handlers, ...) {
 *      // JVM lets us store this before calling the superclass constructor
 *     GUICE$HANDLERS = handlers;
 *     super(...);
 *   }
 *
 *   public static Object GUICE$TRAMPOLINE(int index, Object context, Object[] args) {
 *     switch (index) {
 *       case 0: {
 *         return new HostClass$$EnhancerByGuice((InvocationHandler[]) context, ...);
 *       }
 *       case 1: {
 *         return context.super.instanceMethod(...); // call original unenhanced method
 *       }
 *     }
 *     return null;
 *   }
 *
 *   // enhanced method
 *   public final Object instanceMethod(...) {
 *     // pack arguments and trigger the associated InterceptorStackCallback
 *     return GUICE$HANDLERS[0].invoke(this, null, args);
 *   }
 *
 *   // ...
 * }
 * </pre>
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

  // Describes the LambdaMetafactory.metafactory method arguments and return type
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

  private final String checkcastToProxy;

  Enhancer(Class<?> hostClass, Map<Method, Method> bridgeDelegates) {
    super(hostClass, ENHANCER_BY_GUICE_MARKER);
    this.bridgeDelegates = bridgeDelegates;

    // CHECKCAST(proxyName) fails when hosted anonymously; hostName works in that scenario
    this.checkcastToProxy = ClassDefining.isAnonymousHost(hostClass) ? hostName : proxyName;
  }

  @Override
  protected byte[] generateGlue(Collection<Executable> members) {
    ClassWriter cw = new ClassWriter(COMPUTE_MAXS);

    // target Java8 because that's all we need for the generated trampoline code
    cw.visit(V1_8, PUBLIC | ACC_SUPER, proxyName, null, hostName, null);
    cw.visitSource(GENERATED_SOURCE, null);

    // this shared field either contains the trampoline or glue to make it into an invoker table
    cw.visitField(PUBLIC | STATIC | FINAL, INVOKERS_NAME, INVOKERS_DESCRIPTOR, null, null)
        .visitEnd();

    setupInvokerTable(cw);

    generateTrampoline(cw, members);

    // this field will hold the handlers configured for this particular enhanced instance
    cw.visitField(PRIVATE | FINAL, HANDLERS_NAME, HANDLERS_DESCRIPTOR, null, null).visitEnd();

    Set<Method> remainingBridgeMethods = new HashSet<>(bridgeDelegates.keySet());

    int methodIndex = 0;
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

  /** Generate static initializer to setup invoker table based on the trampoline. */
  private void setupInvokerTable(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(PRIVATE | STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();

    Handle trampolineHandle =
        new Handle(H_INVOKESTATIC, proxyName, TRAMPOLINE_NAME, TRAMPOLINE_DESCRIPTOR, false);

    if (ClassDefining.isAnonymousHost(hostClass)) {
      // proxy class is anonymous we can't create our lambda glue, store raw trampoline instead
      mv.visitLdcInsn(trampolineHandle);
    } else {
      // otherwise generate lambda glue to make the raw trampoline look like an invoker table

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
  }

  /** Generate enhanced constructor that takes a handler array along with the expected arguments. */
  private void enhanceConstructor(ClassWriter cw, Constructor<?> constructor) {
    String descriptor = Type.getConstructorDescriptor(constructor);
    String enhancedDescriptor = '(' + HANDLERS_DESCRIPTOR + descriptor.substring(1);

    MethodVisitor mv =
        cw.visitMethod(PUBLIC, "<init>", enhancedDescriptor, null, exceptionNames(constructor));

    mv.visitCode();

    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ALOAD, 1);
    // store handlers before invoking the superclass constructor (JVM allows this)
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

  /** Generate enhanced method that calls the handler with the same index. */
  private void enhanceMethod(ClassWriter cw, Method method, int methodIndex) {
    MethodVisitor mv =
        cw.visitMethod(
            FINAL | (method.getModifiers() & ~(ABSTRACT | NATIVE | SYNCHRONIZED)),
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
    // we don't use the method argument in InterceptorStackCallback.invoke, so can use null here
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

    mv.visitVarInsn(ALOAD, 1);
    mv.visitTypeInsn(CHECKCAST, checkcastToProxy);
    unpackArguments(mv, target.getParameterTypes());

    mv.visitMethodInsn(
        invokeOpcode, hostName, target.getName(), Type.getMethodDescriptor(target), false);

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
            FINAL | (bridge.getModifiers() & ~(ABSTRACT | NATIVE | SYNCHRONIZED)),
            bridge.getName(),
            Type.getMethodDescriptor(bridge),
            null,
            exceptionNames(bridge));

    mv.visitVarInsn(ALOAD, 0);
    mv.visitTypeInsn(CHECKCAST, checkcastToProxy);

    Class<?>[] bridgeParameterTypes = bridge.getParameterTypes();
    Class<?>[] targetParameterTypes = target.getParameterTypes();

    int slot = 1;
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
