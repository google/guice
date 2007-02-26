/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;

/**
 * Looks up line numbers for classes and their members.
 *
 * @author Chris Nokleberg
 */
public class LineNumbers {

  private final Class cls;
  private Map<String, Integer> lines = new HashMap<String, Integer>();
  private String source;
  private int firstLine = Integer.MAX_VALUE;

  /**
   * Reads line number information from the given class, if available.
   *
   * @param cls the class to read line number information from
   * @throws IllegalArgumentException if the bytecode for the class cannot be
   * found
   * @throws IOException if an error occurs while reading bytecode
   */
  public LineNumbers(Class cls) throws IOException {
    this.cls = cls;
    InputStream in = cls
        .getResourceAsStream("/" + cls.getName().replace('.', '/') + ".class");
    if (in == null) {
      throw new IllegalArgumentException("Cannot find bytecode for " + cls);
    }
    new ClassReader(in).accept(new LineNumberReader(), 0);
  }

  /**
   * Get the source file name as read from the bytecode.
   *
   * @return the source file name if available, or null
   */
  public String getSource() {
    return source;
  }

  /**
   * Get the line number associated with the given member.
   *
   * @param member a field, constructor, or method belonging to the class used
   * during construction
   * @return the wrapped line number, or null if not available
   * @throws IllegalArgumentException if the member does not belong to the
   * class used during construction
   */
  public Integer getLineNumber(Member member) {
    if (!cls.equals(member.getDeclaringClass())) {
      throw new IllegalArgumentException("Member " + member + " belongs to "
          + member.getDeclaringClass() + ", not " + cls);
    }
    return lines.get(getKey(member));
  }

  /**
   * Gets the first line number.
   */
  public int getFirstLine() {
    return firstLine == Integer.MAX_VALUE ? 1 : firstLine;
  }

  private static String getKey(Member member) {
    if (member instanceof Field) {
      return member.getName();
    }
    else if (member instanceof Method) {
      return member.getName() + Type.getMethodDescriptor((Method) member);
    }
    else {
      return "<init>" + Type.getConstructorDescriptor((Constructor) member);
    }
  }

  private class LineNumberReader implements ClassVisitor, MethodVisitor {

    private int line = -1;
    private String pendingMethod;
    private String name;

    public void visit(int version, int access, String name, String signature,
        String superName, String[] interfaces) {
      this.name = name;
    }

    public MethodVisitor visitMethod(int access, String name, String desc,
        String signature, String[] exceptions) {
      if ((access & Opcodes.ACC_PRIVATE) != 0) {
        return null;
      }
      pendingMethod = name + desc;
      line = -1;
      return this;
    }

    public void visitSource(String source, String debug) {
      LineNumbers.this.source = source;
    }

    public void visitLineNumber(int line, Label start) {
      if (line < firstLine) {
        firstLine = line;
      }

      this.line = line;
      if (pendingMethod != null) {
        lines.put(pendingMethod, line);
        pendingMethod = null;
      }
    }

    public void visitFieldInsn(int opcode, String owner, String name,
        String desc) {
      if (opcode == Opcodes.PUTFIELD && this.name.equals(owner)
          && !lines.containsKey(name) && line != -1) {
        lines.put(name, line);
      }
    }

    public void visitEnd() {
    }

    public void visitInnerClass(String name, String outerName, String innerName,
        int access) {
    }

    public void visitOuterClass(String owner, String name, String desc) {
    }

    public void visitAttribute(Attribute attr) {
    }

    public FieldVisitor visitField(int access, String name, String desc,
        String signature, Object value) {
      return null;
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return null;
    }

    public AnnotationVisitor visitAnnotationDefault() {
      return null;
    }

    public AnnotationVisitor visitParameterAnnotation(int parameter,
        String desc, boolean visible) {
      return null;
    }

    public void visitCode() {
    }

    public void visitFrame(int type, int nLocal, Object[] local, int nStack,
        Object[] stack) {
    }

    public void visitIincInsn(int var, int increment) {
    }

    public void visitInsn(int opcode) {
    }

    public void visitIntInsn(int opcode, int operand) {
    }

    public void visitJumpInsn(int opcode, Label label) {
    }

    public void visitLabel(Label label) {
    }

    public void visitLdcInsn(Object cst) {
    }

    public void visitLocalVariable(String name, String desc, String signature,
        Label start, Label end, int index) {
    }

    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    }

    public void visitMaxs(int maxStack, int maxLocals) {
    }

    public void visitMethodInsn(int opcode, String owner, String name,
        String desc) {
    }

    public void visitMultiANewArrayInsn(String desc, int dims) {
    }

    public void visitTableSwitchInsn(int min, int max, Label dflt,
        Label[] labels) {
    }

    public void visitTryCatchBlock(Label start, Label end, Label handler,
        String type) {
    }

    public void visitTypeInsn(int opcode, String desc) {
    }

    public void visitVarInsn(int opcode, int var) {
    }
  }
}
