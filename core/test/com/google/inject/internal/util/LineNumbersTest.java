/*
 * Copyright (C) 2008 Google Inc.
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

import static com.google.inject.Asserts.assertContains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.InternalFlags;
import com.google.inject.matcher.Matchers;
import java.lang.reflect.Modifier;
import javax.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author jessewilson@google.com (Jesse Wilson) */
@RunWith(JUnit4.class)
public class LineNumbersTest {

  @Test
  public void testLineNumbers() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(A.class);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "No implementation for LineNumbersTest$B was bound.",
          "for 1st parameter b",
          "at LineNumbersTest$1.configure");
    }
  }

  static class A {
    @Inject
    A(B b) {}
  }

  public interface B {}

  @Test
  public void testCanHandleLineNumbersForGuiceGeneratedClasses() {
    assumeTrue(InternalFlags.isBytecodeGenEnabled());

    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bindInterceptor(
                  Matchers.only(A.class),
                  Matchers.any(),
                  new MethodInterceptor() {
                    @Override
                    public Object invoke(MethodInvocation methodInvocation) {
                      return null;
                    }
                  });

              bind(A.class);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "No implementation for LineNumbersTest$B was bound.",
          "for 1st parameter b",
          "at LineNumbersTest$2.configure");
    }
  }

  static class GeneratingClassLoader extends ClassLoader {
    static String name = "__generated";

    GeneratingClassLoader() {
      super(B.class.getClassLoader());
    }

    Class<?> generate() {
      org.objectweb.asm.ClassWriter cw =
          new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
      cw.visit(
          org.objectweb.asm.Opcodes.V1_5,
          Modifier.PUBLIC,
          name,
          null,
          org.objectweb.asm.Type.getInternalName(Object.class),
          null);

      String sig = "(" + org.objectweb.asm.Type.getDescriptor(B.class) + ")V";

      org.objectweb.asm.MethodVisitor mv =
          cw.visitMethod(Modifier.PUBLIC, "<init>", sig, null, null);

      mv.visitAnnotation(org.objectweb.asm.Type.getDescriptor(Inject.class), true);
      mv.visitCode();
      mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
      mv.visitMethodInsn(
          org.objectweb.asm.Opcodes.INVOKESPECIAL,
          org.objectweb.asm.Type.getInternalName(Object.class),
          "<init>",
          "()V");
      mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
      cw.visitEnd();

      byte[] buf = cw.toByteArray();

      return defineClass(name.replace('/', '.'), buf, 0, buf.length);
    }
  }

  @Test
  public void testUnavailableByteCodeShowsUnknownSource() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(new GeneratingClassLoader().generate());
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "No implementation for LineNumbersTest$B was bound.",
          "for 1st parameter",
          "at LineNumbersTest$3.configure");
    }
  }

  @Test
  public void testGeneratedClassesCanSucceed() {
    final Class<?> generated = new GeneratingClassLoader().generate();
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(generated);
                bind(B.class).toInstance(new B() {});
              }
            });
    Object instance = injector.getInstance(generated);
    assertEquals(instance.getClass(), generated);
  }
}
