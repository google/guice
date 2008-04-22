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


package com.google.inject;

import com.google.inject.internal.*;
import static com.google.inject.internal.Objects.nonNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.util.*;

/**
 * Reflection that writes reflected data to a generated class.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class CodeGenReflectionFactory implements Reflection.Factory {
  private static final String generatedCodePackage = "com.google.inject";
  private final String name;
  private Map<Class<?>, ConstructionProxy<?>> constructionProxies
      = new HashMap<Class<?>, ConstructionProxy<?>>();

  /**
   * @param name uniquely identifies this reflection instance. This name needs
   *     to be used both at code generation time and then later at runtime.
   */
  public CodeGenReflectionFactory(String name) {
    this.name = name;
  }

  public Reflection create(ErrorHandler errorHandler,
      ConstructionProxyFactory constructionProxyFactory) {
    Reflection delegate = new RuntimeReflectionFactory()
        .create(errorHandler, constructionProxyFactory);
    return new CodeGenReflection(delegate);
  }


  public Reflection.Factory getRuntimeReflectionFactory() {
    final Reflection reflection;
    try {
      reflection = (Reflection) Class.forName(getGeneratedClassName()).newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build reflection class for \""
          + name + "\", has code been generated yet?");
    }

    return new Reflection.Factory() {
      public Reflection create(ErrorHandler errorHandler,
          ConstructionProxyFactory constructionProxyFactory) {
        return reflection;
      }
    };
  }

  private String getGeneratedClassName() {
    return generatedCodePackage + "." + generatedClassSimpleName();
  }

  private class CodeGenReflection implements Reflection {
    private final Reflection delegate;

    private CodeGenReflection(Reflection delegate) {
      this.delegate = nonNull(delegate, "delegate");
    }

    public <T> ConstructionProxy<T> getConstructionProxy(Class<T> implementation) {
      ConstructionProxy<T> result = delegate.getConstructionProxy(implementation);
      constructionProxies.put(implementation, result);
      return result;
    }
  }

  /**
   * Writes generated .java files to {@code generatedSourceDirectory}.
   */
  void writeToFile(File generatedSourceDirectory) throws IOException {
    JavaCodeGenerator out = JavaCodeGenerator.open(generatedSourceDirectory,
        generatedCodePackage, generatedClassSimpleName());
    new ClassWriter(out).writeClass();
    out.close();
  }

  private String generatedClassSimpleName() {
    return "Generated_" + name;
  }

  private class ClassWriter {
    final JavaCodeGenerator out;

    ClassWriter(JavaCodeGenerator out) {
      this.out = out;
    }

    void writeClass() throws IOException {
      out.writePackageHeader();
      out.writeImport(Reflection.class);
      out.writeImport(ConstructionProxy.class);
      out.writeImport(InvocationTargetException.class);
      out.writeImport(Parameter.class);
      out.writeImport(List.class);
      out.writeImport(Member.class);
      out.writeImport(Parameter.class);
      out.writeImport(Nullability.class);
      out.writeImport(Arrays.class);
      out.writeImport(SuppressWarnings.class);
      out.writeImport(Key.class);
      out.writeImport(IllegalArgumentException.class);
      out.writeImport(Object.class);
      for (Map.Entry<Class<?>, ConstructionProxy<?>> entry : constructionProxies.entrySet()) {
        out.writeImport(entry.getKey());
        for (Parameter<?> parameter : entry.getValue().getParameters()) {
          out.writeImport(parameter.getKey().getTypeLiteral().getType());
        }
      }

      out.writeLine()
          .openScope("public class %s implements %s {", generatedClassSimpleName(), Reflection.class)
          .writeLine();

      writeGetConstructionProxy();

      out.writeLine()
          .closeScope("}");
    }

    String keyLiteral(Key<?> key) {
      if (!(key.getTypeLiteral().getType() instanceof Class)) {
        throw new UnsupportedOperationException("TODO");
      }
      if (key.getAnnotationType() != null) {
        throw new UnsupportedOperationException("TODO");
      }
      return String.format("Key.get(%s.class)", out.typeName(key.getTypeLiteral().getType()));
    }


    void writeGetConstructionProxy() throws IOException {
      out.writeLine("@%s(\"unchecked\")", SuppressWarnings.class)
          .openScope("public <T> %s<T> getConstructionProxy(%s<T> implementation) {", ConstructionProxy.class, Class.class);

      for (Map.Entry<Class<?>, ConstructionProxy<?>> entry : constructionProxies.entrySet()) {
        Class<?> implementation = entry.getKey();
        out.openScope("if (implementation == %s.class) {", implementation)
            .openScope("return (%s) new %s<%s>() {", ConstructionProxy.class, ConstructionProxy.class, implementation);

        // newInstance
        out.openScope("public %s newInstance(final %s... arguments) throws %s {", implementation, Object.class, InvocationTargetException.class)
            .openScope("return new %s(", implementation);
        int argument = 0;
        for (Iterator<Parameter<?>> i = entry.getValue().getParameters().iterator(); i.hasNext(); ) {
          Parameter<?> parameter = i.next();
          String separator = i.hasNext() ? "," : "";
          out.writeLine("(%s) arguments[%d]%s", parameter.getKey().getTypeLiteral().getType(), argument, separator);
          argument++;
        }
        out.closeScope(");")
            .closeScope("}");

        // getParameters
        out.openScope("public %s<%s<?>> getParameters() {", List.class, Parameter.class)
            .openScope("return %s.<%s<?>>asList(", Arrays.class, Parameter.class);
        argument = 0;
        for (Iterator<Parameter<?>> i = entry.getValue().getParameters().iterator(); i.hasNext(); ) {
          Parameter<?> parameter = i.next();
          String separator = i.hasNext() ? "," : "";
          out.writeLine("%s.create(%s, %s, %s.%s)%s", Parameter.class, argument, keyLiteral(parameter.getKey()), Nullability.class, parameter.getNullability(), separator);
          argument++;
        }
        out.closeScope(");")
            .closeScope("}");

        // getMember
        out.openScope("public %s getMember() {", Member.class)
            .writeLine("return null;")
            .closeScope("}");

        out.closeScope("};")
            .closeScope("}");
      }
      out.writeLine()
          .writeLine("throw new %s();", IllegalArgumentException.class)
          .closeScope("}");
    }
  }
}
