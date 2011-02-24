/**
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

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import java.lang.reflect.Modifier;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class LineNumbersTest extends TestCase {

  public void testLineNumbers() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(A.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + B.class.getName() + " was bound.",
          "for parameter 0 at " + A.class.getName() + ".<init>(LineNumbersTest.java:",
          "at " + LineNumbersTest.class.getName(), ".configure(LineNumbersTest.java:");
    }
  }

  static class A {
    @Inject A(B b) {}
  }
  public interface B {}

  /*if[AOP]*/
  public void testCanHandleLineNumbersForGuiceGeneratedClasses() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bindInterceptor(Matchers.only(A.class), Matchers.any(),
              new org.aopalliance.intercept.MethodInterceptor() {
                public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation) {
                  return null;
                }
              });

          bind(A.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + B.class.getName() + " was bound.",
          "for parameter 0 at " + A.class.getName() + ".<init>(LineNumbersTest.java:",
          "at " + LineNumbersTest.class.getName(), ".configure(LineNumbersTest.java:");
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
      cw.visit(org.objectweb.asm.Opcodes.V1_5,
          Modifier.PUBLIC, name, null,
          org.objectweb.asm.Type.getInternalName(Object.class), null);

      String sig = "("+org.objectweb.asm.Type.getDescriptor(B.class)+")V";

      org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Modifier.PUBLIC, "<init>", sig, null, null);

      mv.visitAnnotation(org.objectweb.asm.Type.getDescriptor(Inject.class), true);
      mv.visitCode();
      mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
      mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL,
          org.objectweb.asm.Type.getInternalName(Object.class), "<init>", "()V" );
      mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
      cw.visitEnd();

      byte[] buf = cw.toByteArray();

      return defineClass(name.replace('/', '.'), buf, 0, buf.length);
    }
  }

  public void testUnavailableByteCodeShowsUnknownSource() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(new GeneratingClassLoader().generate());
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + B.class.getName() + " was bound.",
          "for parameter 0 at " + GeneratingClassLoader.name + ".<init>(Unknown Source)",
          "at " + LineNumbersTest.class.getName(), ".configure(LineNumbersTest.java:");
    }
  }
  
  public void testGeneratedClassesCanSucceed() {
    final Class<?> generated = new GeneratingClassLoader().generate();
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(generated);
        bind(B.class).toInstance(new B() {});
      }
    });
    Object instance = injector.getInstance(generated);
    assertEquals(instance.getClass(), generated);
  }
  /*end[AOP]*/
}
