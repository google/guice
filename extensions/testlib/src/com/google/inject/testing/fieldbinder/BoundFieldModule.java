/*
 * Copyright (C) 2014 Google Inc.
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

package com.google.inject.testing.fieldbinder;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Annotations;
import com.google.inject.spi.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Automatically creates Guice bindings for fields in an object annotated with {@link Bind}.
 *
 * <p>This module is intended for use in tests to reduce the code needed to bind local fields
 * (usually mocks) for injection.
 *
 * <p>The following rules are followed in determining how fields are bound using this module:
 *
 * <ul>
 * <li>
 * For each {@link Bind} annotated field of an object and its superclasses, this module will bind
 * that field's type to that field's value at injector creation time. This includes both instance
 * and static fields.
 * </li>
 * <li>
 * If {@link Bind#to} is specified, the field's value will be bound to the class specified by
 * {@link Bind#to} instead of the field's actual type.
 * </li>
 * <li>
 * If a {@link BindingAnnotation} or {@link javax.inject.Qualifier} is present on the field,
 * that field will be bound using that annotation via {@link AnnotatedBindingBuilder#annotatedWith}.
 * For example, {@code bind(Foo.class).annotatedWith(BarAnnotation.class).toInstance(theValue)}.
 * It is an error to supply more than one {@link BindingAnnotation} or
 * {@link javax.inject.Qualifier}.
 * </li>
 * <li>
 * If the field is of type {@link Provider}, the field's value will be bound as a {@link Provider}
 * using {@link LinkedBindingBuilder#toProvider} to the provider's parameterized type. For example,
 * {@code Provider<Integer>} binds to {@link Integer}. Attempting to bind a non-parameterized
 * {@link Provider} without a {@link Bind#to} clause is an error.
 * </li>
 * </ul>
 *
 * <p>Example use:
 * <pre><code>
 * public class TestFoo {
 *   // bind(new TypeLiteral{@code <List<Object>>}() {}).toInstance(listOfObjects);
 *   {@literal @}Bind private List{@code <Object>} listOfObjects = Lists.of();
 *   
 *   // bind(String.class).toProvider(new Provider() { public String get() { return userName; }});
 *   {@literal @}Bind(lazy = true) private String userName;
 *
 *   // bind(SuperClass.class).toInstance(aSubClass);
 *   {@literal @}Bind(to = SuperClass.class) private SubClass aSubClass = new SubClass();
 *
 *   // bind(Object.class).annotatedWith(MyBindingAnnotation.class).toInstance(object2);
 *   {@literal @}Bind
 *   {@literal @}MyBindingAnnotation
 *   private String myString = "hello";
 *
 *   // bind(Object.class).toProvider(myProvider);
 *   {@literal @}Bind private Provider{@code <Object>} myProvider = getProvider();
 *
 *   {@literal @}Before public void setUp() {
 *     Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
 *   }
 * }
 * </code></pre>
 *
 * @see Bind
 * @author eatnumber1@google.com (Russ Harmon)
 */
public final class BoundFieldModule implements Module {
  private final Object instance;

  // Note that binder is not initialized until configure() is called.
  private Binder binder;

  private BoundFieldModule(Object instance) {
    this.instance = instance;
  }

  /**
   * Create a BoundFieldModule which binds the {@link Bind} annotated fields of {@code instance}.
   *
   * @param instance the instance whose fields will be bound.
   * @return a module which will bind the {@link Bind} annotated fields of {@code instance}.
   */
  public static BoundFieldModule of(Object instance) {
    return new BoundFieldModule(instance);
  }

  private static class BoundFieldException extends RuntimeException {
    private final Message message;

    BoundFieldException(Message message) {
      super(message.getMessage());
      this.message = message;
    }
  }

  private class BoundFieldInfo {
    /** The field itself. */
    final Field field;

    /**
     * The actual type of the field.
     *
     * <p>For example, {@code @Bind(to = Object.class) Number one = new Integer(1);} will be
     * {@link Number}.
     */
    final TypeLiteral<?> type;

    /** The {@link Bind} annotation which is present on the field. */
    final Bind bindAnnotation;

    /**
     * The type this field will bind to.
     *
     * <p>For example, {@code @Bind(to = Object.class) Number one = new Integer(1);} will be
     * {@link Object} and {@code @Bind Number one = new Integer(1);} will be {@link Number}.
     */
    final TypeLiteral<?> boundType;

    /**
     * The "natural" type of this field.
     *
     * <p>For example, {@code @Bind(to = Object.class) Number one = new Integer(1);} will be
     * {@link Number}, and {@code @Bind(to = Object.class) Provider<Number> one = new Integer(1);}
     * will be {@link Number}.
     *
     * @see #getNaturalFieldType
     */
    final Optional<TypeLiteral<?>> naturalType;

    BoundFieldInfo(
        Field field,
        Bind bindAnnotation,
        TypeLiteral<?> fieldType) {
      this.field = field;
      this.type = fieldType;
      this.bindAnnotation = bindAnnotation;

      field.setAccessible(true);

      this.naturalType = getNaturalFieldType();
      this.boundType = getBoundType();
    }

    private TypeLiteral<?> getBoundType() {
      Class<?> bindClass = bindAnnotation.to();
      // Bind#to's default value is Bind.class which is used to represent that no explicit binding
      // type is requested.
      if (bindClass == Bind.class) {
        Preconditions.checkState(naturalType != null);
        if (!this.naturalType.isPresent()) {
          throwBoundFieldException(
              field,
              "Non parameterized Provider fields must have an explicit "
              + "binding class via @Bind(to = Foo.class)");
        }
        return this.naturalType.get();
      } else {
        return TypeLiteral.get(bindClass);
      }
    }

    /**
     * Retrieves the type this field binds to naturally.
     *
     * <p>A field's "natural" type specifically ignores the to() method on the @Bind annotation, is
     * the parameterized type if the field's actual type is a parameterized {@link Provider}, is
     * {@link Optional#absent()} if this field is a non-parameterized {@link Provider} and otherwise
     * is the field's actual type.
     *
     * @return the type this field binds to naturally, or {@link Optional#absent()} if this field is
     * a non-parameterized {@link Provider}.
     */
    private Optional<TypeLiteral<?>> getNaturalFieldType() {
      if (isTransparentProvider(type.getRawType())) {
        Type providerType = type.getType();
        if (providerType instanceof Class) {
          return Optional.absent();
        }
        Preconditions.checkState(providerType instanceof ParameterizedType);
        Type[] providerTypeArguments = ((ParameterizedType) providerType).getActualTypeArguments();
        Preconditions.checkState(providerTypeArguments.length == 1);
        return Optional.<TypeLiteral<?>>of(TypeLiteral.get(providerTypeArguments[0]));
      } else {
        return Optional.<TypeLiteral<?>>of(type);
      }
    }

    Object getValue() {
      try {
        return field.get(instance);
      } catch (IllegalAccessException e) {
        // Since we called setAccessible(true) on this field in the constructor, this is a
        // programming error if it occurs.
        throw new AssertionError(e);
      }
    }
  }

  private static boolean hasInject(Field field) {
    return field.isAnnotationPresent(javax.inject.Inject.class)
        || field.isAnnotationPresent(com.google.inject.Inject.class);
  }

  /**
   * Retrieve a {@link BoundFieldInfo}.
   *
   * <p>This returns a {@link BoundFieldInfo} if the field has a {@link Bind} annotation.
   * Otherwise it returns {@link Optional#absent()}.
   */
  private Optional<BoundFieldInfo> getBoundFieldInfo(
      TypeLiteral<?> containingClassType,
      Field field) {
    Bind bindAnnotation = field.getAnnotation(Bind.class);
    if (bindAnnotation == null) {
      return Optional.absent();
    }
    if (hasInject(field)) {
      throwBoundFieldException(
          field,
          "Fields annotated with both @Bind and @Inject are illegal.");
    }
    return Optional.of(
        new BoundFieldInfo(
            field,
            bindAnnotation,
            containingClassType.getFieldType(field)));
  }

  private LinkedBindingBuilder<?> verifyBindingAnnotations(
      Field field,
      AnnotatedBindingBuilder<?> annotatedBinder) {
    LinkedBindingBuilder<?> binderRet = annotatedBinder;
    for (Annotation annotation : field.getAnnotations()) {
      Class<? extends Annotation> annotationType = annotation.annotationType();
      if (Annotations.isBindingAnnotation(annotationType)) {
        // not returning here ensures that annotatedWith will be called multiple times if this field
        // has multiple BindingAnnotations, relying on the binder to throw an error in this case.
        binderRet = annotatedBinder.annotatedWith(annotation);
      }
    }
    return binderRet;
  }

  /**
   * Determines if {@code clazz} is a "transparent provider".
   *
   * <p>A transparent provider is a {@link com.google.inject.Provider} or
   * {@link javax.inject.Provider} which binds to it's parameterized type when used as the argument
   * to {@link Binder#bind}.
   *
   * <p>A {@link Provider} is transparent if the base class of that object is {@link Provider}. In
   * other words, subclasses of {@link Provider} are not transparent. As a special case, if a
   * {@link Provider} has no parameterized type but is otherwise transparent, then it is considered
   * transparent.
   */
  private static boolean isTransparentProvider(Class<?> clazz) {
    return com.google.inject.Provider.class == clazz || javax.inject.Provider.class == clazz;
  }

  private void bindField(final BoundFieldInfo fieldInfo) {
    if (fieldInfo.naturalType.isPresent()) {
      Class<?> naturalRawType = fieldInfo.naturalType.get().getRawType();
      Class<?> boundRawType = fieldInfo.boundType.getRawType();
      if (!boundRawType.isAssignableFrom(naturalRawType)) {
        throwBoundFieldException(
            fieldInfo.field,
            "Requested binding type \"%s\" is not assignable from field binding type \"%s\"",
            boundRawType.getName(),
            naturalRawType.getName());
      }
    }

    AnnotatedBindingBuilder<?> annotatedBinder = binder.bind(fieldInfo.boundType);
    LinkedBindingBuilder<?> binder = verifyBindingAnnotations(fieldInfo.field, annotatedBinder);

    // It's unfortunate that Field.get() just returns Object rather than the actual type (although
    // that would be impossible) because as a result calling binder.toInstance or binder.toProvider
    // is impossible to do without an unchecked cast. This is safe if fieldInfo.naturalType is
    // present because compatibility is checked explicitly above, but is _unsafe_ if
    // fieldInfo.naturalType is absent which occurrs when a non-parameterized Provider is used with
    // @Bind(to = ...)
    @SuppressWarnings("unchecked")
    AnnotatedBindingBuilder<Object> binderUnsafe = (AnnotatedBindingBuilder<Object>) binder;

    if (isTransparentProvider(fieldInfo.type.getRawType())) {
      if (fieldInfo.bindAnnotation.lazy()) {
        // We don't support this because it is confusing about when values are captured.
        throwBoundFieldException(fieldInfo.field, 
            "'lazy' is incompatible with Provider valued fields");
      }
      // This is safe because we checked that the field's type is Provider above.
      @SuppressWarnings("unchecked")
      Provider<?> fieldValueUnsafe = (Provider<?>) getFieldValue(fieldInfo);
      binderUnsafe.toProvider(fieldValueUnsafe);
    } else if (fieldInfo.bindAnnotation.lazy()) {
      binderUnsafe.toProvider(new Provider<Object>() {
        @Override public Object get() {
          return getFieldValue(fieldInfo);
        }
      });
    } else {
      binderUnsafe.toInstance(getFieldValue(fieldInfo));
    }
  }

  private Object getFieldValue(final BoundFieldInfo fieldInfo) {
    Object fieldValue = fieldInfo.getValue();
    if (fieldValue == null) {
      throwBoundFieldException(
          fieldInfo.field,
          "Binding to null values is not allowed. "
              + "Use Providers.of(null) if this is your intended behavior.",
              fieldInfo.field.getName());
    }
    return fieldValue;
  }

  private void throwBoundFieldException(Field field, String format, Object... args) {
    Preconditions.checkNotNull(binder);
    String source = String.format(
        "%s field %s",
        field.getDeclaringClass().getName(),
        field.getName());
    throw new BoundFieldException(new Message(source, String.format(format, args)));
  }

  @Override
  public void configure(Binder binder) {
    binder = binder.skipSources(BoundFieldModule.class);
    this.binder = binder;

    TypeLiteral<?> currentClassType = TypeLiteral.get(instance.getClass());
    while (currentClassType.getRawType() != Object.class) {
      for (Field field : currentClassType.getRawType().getDeclaredFields()) {
        try {
          Optional<BoundFieldInfo> fieldInfoOpt =
              getBoundFieldInfo(currentClassType, field);
          if (fieldInfoOpt.isPresent()) {
            bindField(fieldInfoOpt.get());
          }
        } catch (BoundFieldException e) {
          // keep going to try to collect as many errors as possible
          binder.addError(e.message);
        }
      }
      currentClassType =
          currentClassType.getSupertype(currentClassType.getRawType().getSuperclass());
    }
  }
}
