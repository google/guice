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

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.SIPUSH;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Utility methods to generate common bytecode tasks.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class BytecodeTasks {
  private BytecodeTasks() {}

  /** Pushes an integer onto the stack, choosing the most efficient opcode. */
  public static void pushInteger(MethodVisitor mv, int value) {
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

  /** Packs local arguments into an argument array on the Java stack. */
  public static void packArguments(MethodVisitor mv, Class<?>[] parameterTypes) {
    pushInteger(mv, parameterTypes.length);
    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
    int parameterIndex = 0;
    int slot = 1;
    for (Class<?> parameterType : parameterTypes) {
      mv.visitInsn(DUP);
      pushInteger(mv, parameterIndex++);
      slot += loadArgument(mv, parameterType, slot);
      if (parameterType.isPrimitive()) {
        box(mv, Type.getType(parameterType));
      }
      mv.visitInsn(AASTORE);
    }
  }

  /** Unpacks an array of arguments and pushes them onto the Java stack. */
  public static void unpackArguments(MethodVisitor mv, Class<?>[] parameterTypes) {
    int parameterIndex = 0;
    for (Class<?> parameterType : parameterTypes) {
      // invoker pattern means we can safely assume array is the second local argument
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

  /** Loads a local argument onto the Java stack and returns the size of the argument. */
  public static int loadArgument(MethodVisitor mv, Class<?> parameterType, int slot) {
    if (!parameterType.isPrimitive()) {
      mv.visitVarInsn(ALOAD, slot);
      return 1;
    }

    Type primitiveType = Type.getType(parameterType);
    mv.visitVarInsn(primitiveType.getOpcode(ILOAD), slot);
    return primitiveType.getSize();
  }

  /** Boxes a primitive value on the Java stack. */
  public static void box(MethodVisitor mv, Type primitiveType) {
    String wrapper;
    String descriptor;

    switch (primitiveType.getSort()) {
      case Type.BOOLEAN:
        wrapper = "java/lang/Boolean";
        descriptor = "(Z)Ljava/lang/Boolean;";
        break;
      case Type.CHAR:
        wrapper = "java/lang/Character";
        descriptor = "(C)Ljava/lang/Character;";
        break;
      case Type.BYTE:
        wrapper = "java/lang/Byte";
        descriptor = "(B)Ljava/lang/Byte;";
        break;
      case Type.SHORT:
        wrapper = "java/lang/Short";
        descriptor = "(S)Ljava/lang/Short;";
        break;
      case Type.INT:
        wrapper = "java/lang/Integer";
        descriptor = "(I)Ljava/lang/Integer;";
        break;
      case Type.FLOAT:
        wrapper = "java/lang/Float";
        descriptor = "(F)Ljava/lang/Float;";
        break;
      case Type.LONG:
        wrapper = "java/lang/Long";
        descriptor = "(J)Ljava/lang/Long;";
        break;
      case Type.DOUBLE:
        wrapper = "java/lang/Double";
        descriptor = "(D)Ljava/lang/Double;";
        break;
      default:
        return;
    }

    mv.visitMethodInsn(INVOKESTATIC, wrapper, "valueOf", descriptor, false);
  }

  /** Unboxes a boxed value on the Java stack. */
  public static void unbox(MethodVisitor mv, Type primitiveType) {
    String wrapper;
    String method;
    String descriptor;

    switch (primitiveType.getSort()) {
      case Type.BOOLEAN:
        wrapper = "java/lang/Boolean";
        method = "booleanValue";
        descriptor = "()Z";
        break;
      case Type.CHAR:
        wrapper = "java/lang/Character";
        method = "charValue";
        descriptor = "()C";
        break;
      case Type.BYTE:
        wrapper = "java/lang/Byte";
        method = "byteValue";
        descriptor = "()B";
        break;
      case Type.SHORT:
        wrapper = "java/lang/Short";
        method = "shortValue";
        descriptor = "()S";
        break;
      case Type.INT:
        wrapper = "java/lang/Integer";
        method = "intValue";
        descriptor = "()I";
        break;
      case Type.FLOAT:
        wrapper = "java/lang/Float";
        method = "floatValue";
        descriptor = "()F";
        break;
      case Type.LONG:
        wrapper = "java/lang/Long";
        method = "longValue";
        descriptor = "()J";
        break;
      case Type.DOUBLE:
        wrapper = "java/lang/Double";
        method = "doubleValue";
        descriptor = "()D";
        break;
      default:
        return;
    }

    mv.visitTypeInsn(CHECKCAST, wrapper);
    mv.visitMethodInsn(INVOKEVIRTUAL, wrapper, method, descriptor, false);
  }
}
