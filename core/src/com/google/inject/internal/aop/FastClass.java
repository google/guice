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
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.lang.reflect.Executable;
import java.util.Collection;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * Generates fast-classes.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
final class FastClass extends AbstractGlueGenerator {

  public FastClass(Class<?> hostClass) {
    super(hostClass, FASTCLASS_BY_GUICE_MARKER);
  }

  @Override
  protected byte[] generateGlue(Collection<Executable> members) {
    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    cw.visit(V1_8, PUBLIC | FINAL | ACC_SUPER, proxyName, null, hostName, null);
    cw.visitField(PUBLIC | STATIC | FINAL, GLUE_NAME, GLUE_TYPE, null, null).visitEnd();

    mv = cw.visitMethod(PRIVATE | STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    mv.visitLdcInsn(new Handle(H_INVOKESTATIC, proxyName, GLUE_NAME, GLUE_SIGNATURE, false));
    mv.visitFieldInsn(PUTSTATIC, proxyName, GLUE_NAME, GLUE_TYPE);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 0);
    mv.visitEnd();

    int numMembers = members.size();

    mv = cw.visitMethod(PUBLIC | STATIC, GLUE_NAME, GLUE_SIGNATURE, null, null);
    mv.visitCode();

    Label[] labels = new Label[numMembers];
    for (int i = 0; i < numMembers; i++) {
      labels[i] = new Label();
    }
    Label defaultLabel = new Label();

    mv.visitVarInsn(ILOAD, 0);
    mv.visitTableSwitchInsn(0, numMembers - 1, defaultLabel, labels);
    for (int i = 0; i < numMembers; i++) {
      mv.visitLabel(labels[i]);
      mv.visitFrame(F_SAME, 0, null, 0, null);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ARETURN);
    }

    mv.visitLabel(defaultLabel);
    mv.visitFrame(F_SAME, 0, null, 0, null);
    mv.visitInsn(ACONST_NULL);
    mv.visitInsn(ARETURN);

    mv.visitMaxs(1, 3);
    mv.visitEnd();

    cw.visitEnd();

    return cw.toByteArray();
  }
}
