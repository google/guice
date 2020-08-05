/*
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

package com.google.inject.internal.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Looks up line numbers for classes and their members.
 *
 * @author Chris Nokleberg
 */
final class LineNumbers {

  private static final int ASM_API_LEVEL = Opcodes.ASM7;

  private final Class<?> type;
  private final Map<String, Integer> lines = Maps.newHashMap();
  private String source;
  private int firstLine = Integer.MAX_VALUE;

  /**
   * Reads line number information from the given class, if available.
   *
   * @param type the class to read line number information from
   */
  public LineNumbers(Class<?> type) throws IOException {
    this.type = type;

    if (!type.isArray()) {
      InputStream in = null;
      try {
        in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class");
      } catch (IllegalStateException ignored) {
        // Some classloaders throw IllegalStateException when they can't load a resource.
      }
      if (in != null) {
        try {
          new ClassReader(in).accept(new LineNumberReader(), ClassReader.SKIP_FRAMES);
        } catch (UnsupportedOperationException ignored) {
          // We may be trying to inspect classes that were compiled with a more recent version
          // of javac than our ASM supports.  If that happens, just ignore the class and don't
          // capture line numbers.
        } finally {
          try {
            in.close();
          } catch (IOException ignored) {
          }
        }
      }
    }
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
   * @param member a field, constructor, or method belonging to the class used during construction
   * @return the wrapped line number, or null if not available
   * @throws IllegalArgumentException if the member does not belong to the class used during
   *     construction
   */
  public Integer getLineNumber(Member member) {
    Preconditions.checkArgument(
        type == member.getDeclaringClass(),
        "Member %s belongs to %s, not %s",
        member,
        member.getDeclaringClass(),
        type);
    return lines.get(memberKey(member));
  }

  /** Gets the first line number. */
  public int getFirstLine() {
    return firstLine == Integer.MAX_VALUE ? 1 : firstLine;
  }

  private String memberKey(Member member) {
    checkNotNull(member, "member");

    /*if[AOP]*/
    if (member instanceof Field) {
      return member.getName();

    } else if (member instanceof Method) {
      return member.getName() + org.objectweb.asm.Type.getMethodDescriptor((Method) member);

    } else if (member instanceof Constructor) {
      StringBuilder sb = new StringBuilder().append("<init>(");
      for (Class<?> param : ((Constructor<?>) member).getParameterTypes()) {
        sb.append(org.objectweb.asm.Type.getDescriptor(param));
      }
      return sb.append(")V").toString();

    } else {
      throw new IllegalArgumentException(
          "Unsupported implementation class for Member, " + member.getClass());
    }
    /*end[AOP]*/
    /*if[NO_AOP]
    return "<NO_MEMBER_KEY>";
    end[NO_AOP]*/
  }

  private class LineNumberReader extends ClassVisitor {

    private int line = -1;
    private String pendingMethod;
    private String name;

    LineNumberReader() {
      super(ASM_API_LEVEL);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.name = name;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String desc, String signature, String[] exceptions) {
      if ((access & Opcodes.ACC_PRIVATE) != 0) {
        return null;
      }
      pendingMethod = name + desc;
      line = -1;
      return new LineNumberMethodVisitor();
    }

    @Override
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

    @Override
    public FieldVisitor visitField(
        int access, String name, String desc, String signature, Object value) {
      return null;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new LineNumberAnnotationVisitor();
    }

    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      return new LineNumberAnnotationVisitor();
    }

    class LineNumberMethodVisitor extends MethodVisitor {
      LineNumberMethodVisitor() {
        super(ASM_API_LEVEL);
      }

      @Override
      public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return new LineNumberAnnotationVisitor();
      }

      @Override
      public AnnotationVisitor visitAnnotationDefault() {
        return new LineNumberAnnotationVisitor();
      }

      @Override
      public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        if (opcode == Opcodes.PUTFIELD
            && LineNumberReader.this.name.equals(owner)
            && !lines.containsKey(name)
            && line != -1) {
          lines.put(name, line);
        }
      }

      @Override
      public void visitLineNumber(int line, Label start) {
        LineNumberReader.this.visitLineNumber(line, start);
      }
    }

    class LineNumberAnnotationVisitor extends AnnotationVisitor {
      LineNumberAnnotationVisitor() {
        super(ASM_API_LEVEL);
      }

      @Override
      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return this;
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        return this;
      }

      public void visitLocalVariable(
          String name, String desc, String signature, Label start, Label end, int index) {}
    }
  }
}
