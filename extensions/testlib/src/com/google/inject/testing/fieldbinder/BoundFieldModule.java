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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.RestrictedBindingSource;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.MoreTypes;
import com.google.inject.internal.Nullability;
import com.google.inject.spi.Message;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Automatically creates Guice bindings for fields in an object annotated with {@link Bind}.
 *
 * <p>This module is intended for use in tests to reduce the code needed to bind local fields
 * (usually mocks) for injection.
 *
 * <p>The following rules are followed in determining how fields are bound using this module:
 *
 * <ul>
 *   <li>For each {@link Bind} annotated field of an object and its superclasses, this module will
 *       bind that field's type to that field's value at injector creation time. This includes both
 *       instance and static fields.
 *   <li>If {@link Bind#to} is specified, the field's value will be bound to the class specified by
 *       {@link Bind#to} instead of the field's actual type.
 *   <li>If {@link Bind#lazy} is true, this module will delay reading the value from the field until
 *       injection time, allowing the field's value to be reassigned during the course of a test's
 *       execution.
 *   <li>If a {@link BindingAnnotation} or {@link javax.inject.Qualifier} is present on the field,
 *       that field will be bound using that annotation via {@link
 *       AnnotatedBindingBuilder#annotatedWith}. For example, {@code
 *       bind(Foo.class).annotatedWith(BarAnnotation.class).toInstance(theValue)}. It is an error to
 *       supply more than one {@link BindingAnnotation} or {@link javax.inject.Qualifier}.
 *   <li>If the field is of type {@link Provider}, the field's value will be bound as a {@link
 *       Provider} using {@link LinkedBindingBuilder#toProvider} to the provider's parameterized
 *       type. For example, {@code Provider<Integer>} binds to {@link Integer}. Attempting to bind a
 *       non-parameterized {@link Provider} without a {@link Bind#to} clause is an error.
 * </ul>
 *
 * <p>Example use:
 *
 * <pre><code>
 * public class TestFoo {
 *   // bind(new TypeLiteral{@code <List<Object>>}() {}).toInstance(listOfObjects);
 *   {@literal @}Bind private List{@code <Object>} listOfObjects = Lists.of();
 *
 *   // private String userName = "string_that_changes_over_time";
 *   // bind(String.class).toProvider(new Provider() { public String get() { return userName; }});
 *   {@literal @}Bind(lazy = true) private String userName;
 *
 *   // bind(SuperClass.class).toInstance(aSubClass);
 *   {@literal @}Bind(to = SuperClass.class) private SubClass aSubClass = new SubClass();
 *
 *   // bind(String.class).annotatedWith(MyBindingAnnotation.class).toInstance(myString);
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
  private final ImmutableList<Message> deferredBindingErrors;
  private final ImmutableSet<BoundFieldInfo> boundFields;

  private BoundFieldModule(Object instance) {
    this.instance = instance;

    ImmutableList.Builder<Message> deferredErrors = ImmutableList.builder();
    boundFields = findBindableFields(deferredErrors);
    deferredBindingErrors = deferredErrors.build();
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

  /**
   * Wrapper of BoundFieldModule which enables attaching {@link @RestrictedBindingSource} permits to
   * instances of it.
   *
   * <p>To create an instance of BoundFieldModule with permits (to enable it to bind restricted
   * bindings), create an instance of an anonymous class extending this one and annotate it with
   * those permits. For example: {@code new @Permit1 @Permit2 BoundFieldModule.WithPermits(instance)
   * {}}.
   */
  public static class WithPermits extends AbstractModule {
    private final Object instance;

    protected WithPermits(Object instance) {
      this.instance = instance;
      // TODO(user): Enforce this at compile-time (e.g. via ErrorProne).
      Preconditions.checkState(
          getClass().isAnonymousClass()
              && Arrays.stream(getClass().getAnnotatedSuperclass().getAnnotations())
                  .anyMatch(
                      annotation ->
                          annotation
                              .annotationType()
                              .isAnnotationPresent(RestrictedBindingSource.Permit.class)),
          "This class should only be used as a base class for an anonymous class with"
              + " @RestrictedBindingSource.Permit annotations, for example: new @FooPermit"
              + " BoundFieldModule.WithPermits(instance) {}.");
    }

    @Override
    protected void configure() {
      install(BoundFieldModule.of(instance));
    }
  }

  private static class BoundFieldException extends Exception {
    private final Message message;

    BoundFieldException(Message message) {
      super(message.getMessage());
      this.message = message;
    }
  }

  private static class NullBoundFieldValueException extends RuntimeException {
    private final Message message;

    NullBoundFieldValueException(Message message) {
      super(message.toString());
      this.message = message;
    }
  }

  /** Information about a field bound by {@link BoundFieldModule}. */
  public static final class BoundFieldInfo {
    private final Object instance;
    private final Field field;
    private final TypeLiteral<?> fieldType;
    private final Bind bindAnnotation;

    /** @see #getBoundKey */
    private final Key<?> boundKey;

    private BoundFieldInfo(
        Object instance, Field field, Bind bindAnnotation, TypeLiteral<?> fieldType)
        throws BoundFieldException {
      this.instance = instance;
      this.field = field;
      this.fieldType = fieldType;
      this.bindAnnotation = bindAnnotation;

      field.setAccessible(true);
      Annotation bindingAnnotation = computeBindingAnnotation();
      Optional<TypeLiteral<?>> naturalType = computeNaturalFieldType();
      this.boundKey = computeKey(naturalType, bindingAnnotation);
      checkBindingIsAssignable(field, naturalType);
    }

    private void checkBindingIsAssignable(Field field, Optional<TypeLiteral<?>> naturalType)
        throws BoundFieldException {
      if (naturalType.isPresent()) {
        Class<?> boundRawType = boundKey.getTypeLiteral().getRawType();
        Class<?> naturalRawType = MoreTypes.canonicalizeForKey(naturalType.get()).getRawType();
        if (!boundRawType.isAssignableFrom(naturalRawType)) {
          throw new BoundFieldException(
              new Message(
                  field,
                  String.format(
                      "Requested binding type \"%s\" is not assignable "
                          + "from field binding type \"%s\"",
                      boundRawType.getName(), naturalRawType.getName())));
        }
      }
    }

    /** The field itself. */
    public Field getField() {
      return field;
    }

    /**
     * The actual type of the field.
     *
     * <p>For example, {@code @Bind(to = Object.class) Number one = new Integer(1);} will be {@code
     * Number}. {@code @Bind Provider<Number>} will be {@code Provider<Number>}.
     */
    public TypeLiteral<?> getFieldType() {
      return fieldType;
    }

    /**
     * The {@literal @}{@link Bind} annotation which is present on the field.
     *
     * <p>Note this is not the same as the binding annotation (or qualifier) for {@link
     * #getBoundKey()}
     */
    public Bind getBindAnnotation() {
      return bindAnnotation;
    }

    /**
     * The key this field will bind to.
     *
     * <ul>
     *   <li>{@code @Bind(to = Object.class) @MyQualifier Number one = new Integer(1);} will be
     *       {@code @MyQualifier Object}.
     *   <li>{@code @Bind @MyQualifier(2) Number one = new Integer(1);} will be
     *       {@code @MyQualifier(2) Number}.
     *   <li>{@code @Bind @MyQualifier Provider<String> three = "default"} will be
     *       {@code @MyQualfier String}
     * </ul>
     */
    public Key<?> getBoundKey() {
      return boundKey;
    }

    /** Returns the current value of this field. */
    public Object getValue() {
      try {
        return field.get(instance);
      } catch (IllegalAccessException e) {
        // Since we called setAccessible(true) on this field in the constructor, this is a
        // programming error if it occurs.
        throw new AssertionError(e);
      }
    }

    private Annotation computeBindingAnnotation() throws BoundFieldException {
      Annotation found = null;
      for (Annotation annotation : field.getAnnotations()) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (Annotations.isBindingAnnotation(annotationType)) {
          if (found != null) {
            throw new BoundFieldException(
                new Message(field, "More than one annotation is specified for this binding."));
          }
          found = annotation;
        }
      }
      return found;
    }

    private Key<?> computeKey(Optional<TypeLiteral<?>> naturalType, Annotation bindingAnnotation)
        throws BoundFieldException {
      TypeLiteral<?> boundType = computeBoundType(naturalType);
      if (bindingAnnotation == null) {
        return Key.get(boundType);
      } else {
        return Key.get(boundType, bindingAnnotation);
      }
    }

    private TypeLiteral<?> computeBoundType(Optional<TypeLiteral<?>> naturalType)
        throws BoundFieldException {
      Class<?> bindClass = bindAnnotation.to();
      // Bind#to's default value is Bind.class which is used to represent that no explicit binding
      // type is requested.
      if (bindClass == Bind.class) {
        Preconditions.checkState(naturalType != null);
        if (!naturalType.isPresent()) {
          throw new BoundFieldException(
              new Message(
                  field,
                  "Non parameterized Provider fields must have an explicit "
                      + "binding class via @Bind(to = Foo.class)"));
        }
        return naturalType.get();
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
     *     a non-parameterized {@link Provider}.
     */
    private Optional<TypeLiteral<?>> computeNaturalFieldType() {
      if (isTransparentProvider(fieldType.getRawType())) {
        Type providerType = fieldType.getType();
        if (providerType instanceof Class) {
          return Optional.absent();
        }
        Preconditions.checkState(providerType instanceof ParameterizedType);
        Type[] providerTypeArguments = ((ParameterizedType) providerType).getActualTypeArguments();
        Preconditions.checkState(providerTypeArguments.length == 1);
        return Optional.<TypeLiteral<?>>of(TypeLiteral.get(providerTypeArguments[0]));
      } else {
        return Optional.<TypeLiteral<?>>of(fieldType);
      }
    }

    /** Returns whether a binding supports null values. */
    private boolean allowsNull() {
      return !isTransparentProvider(fieldType.getRawType())
          && Nullability.allowsNull(field.getAnnotations());
    }
  }

  /** Returns the the object originally passed to {@link BoundFieldModule#of}). */
  public Object getInstance() {
    return instance;
  }

  /**
   * Returns information about the fields bound by this module.
   *
   * <p>Note this is available immediately after construction, fields with errors won't be included
   * but their error messages will be deferred to configuration time.
   *
   * <p>Fields with invalid null values <em>are</em> included but still cause errors at
   * configuration time.
   */
  public ImmutableSet<BoundFieldInfo> getBoundFields() {
    return boundFields;
  }

  private ImmutableSet<BoundFieldInfo> findBindableFields(
      ImmutableList.Builder<Message> deferredErrors) {
    ImmutableSet.Builder<BoundFieldInfo> fieldInfos = ImmutableSet.builder();
    TypeLiteral<?> currentClassType = TypeLiteral.get(instance.getClass());
    while (currentClassType.getRawType() != Object.class) {
      for (Field field : currentClassType.getRawType().getDeclaredFields()) {
        Optional<BoundFieldInfo> fieldInfoOpt =
            getBoundFieldInfo(currentClassType, field, deferredErrors);
        if (fieldInfoOpt.isPresent()) {
          fieldInfos.add(fieldInfoOpt.get());
        }
      }
      currentClassType =
          currentClassType.getSupertype(currentClassType.getRawType().getSuperclass());
    }
    return fieldInfos.build();
  }

  /**
   * Retrieve a {@link BoundFieldInfo}.
   *
   * <p>This returns a {@link BoundFieldInfo} if the field has a {@link Bind} annotation. Otherwise
   * it returns {@link Optional#absent()}.
   */
  private Optional<BoundFieldInfo> getBoundFieldInfo(
      TypeLiteral<?> containingClassType,
      Field field,
      ImmutableList.Builder<Message> deferredErrors) {
    Bind bindAnnotation = field.getAnnotation(Bind.class);
    if (bindAnnotation == null) {
      return Optional.absent();
    }
    if (hasInject(field)) {
      deferredErrors.add(
          new Message(field, "Fields annotated with both @Bind and @Inject are illegal."));
      return Optional.absent();
    }
    try {
      return Optional.of(
          new BoundFieldInfo(
              instance, field, bindAnnotation, containingClassType.getFieldType(field)));
    } catch (ConfigurationException e) { // thrown from Key.get, MoreTypes.canonicalizeForKey
      deferredErrors.addAll(e.getErrorMessages());
      return Optional.absent();
    } catch (BoundFieldException e) {
      deferredErrors.add(e.message);
      return Optional.absent();
    }
  }

  private static boolean hasInject(Field field) {
    return field.isAnnotationPresent(javax.inject.Inject.class)
        || field.isAnnotationPresent(com.google.inject.Inject.class);
  }

  /**
   * Determines if {@code clazz} is a "transparent provider".
   *
   * <p>If you have traced through the code and found that what you want to do is failing because of
   * this check, try using {@code @Bind(lazy=true) MyType myField} and lazily assign myField
   * instead.
   *
   * <p>A transparent provider is a {@link com.google.inject.Provider} or {@link
   * javax.inject.Provider} which binds to it's parameterized type when used as the argument to
   * {@link Binder#bind}.
   *
   * <p>A {@link Provider} is transparent if the base class of that object is {@link Provider}. In
   * other words, subclasses of {@link Provider} are not transparent. As a special case, if a {@link
   * Provider} has no parameterized type but is otherwise transparent, then it is considered
   * transparent.
   *
   * <p>Subclasses of {@link Provider} are not considered transparent in order to allow users to
   * bind those subclasses directly, enabling them to inject the providers themselves.
   */
  private static boolean isTransparentProvider(Class<?> clazz) {
    return com.google.inject.Provider.class == clazz || javax.inject.Provider.class == clazz;
  }

  private static void bindField(Binder binder, final BoundFieldInfo fieldInfo) {
    LinkedBindingBuilder<?> linkedBinder =
        binder.withSource(fieldInfo.field).bind(fieldInfo.boundKey);

    // It's unfortunate that Field.get() just returns Object rather than the actual type (although
    // that would be impossible) because as a result calling binder.toInstance or binder.toProvider
    // is impossible to do without an unchecked cast. This is safe if fieldInfo.naturalType is
    // present because compatibility is checked explicitly above, but is _unsafe_ if
    // fieldInfo.naturalType is absent which occurrs when a non-parameterized Provider is used with
    // @Bind(to = ...)
    @SuppressWarnings("unchecked")
    AnnotatedBindingBuilder<Object> binderUnsafe = (AnnotatedBindingBuilder<Object>) linkedBinder;

    if (isTransparentProvider(fieldInfo.fieldType.getRawType())) {
      if (fieldInfo.bindAnnotation.lazy()) {
        binderUnsafe.toProvider(
            new Provider<Object>() {
              @Override
              // @Nullable
              public Object get() {
                javax.inject.Provider<?> provider =
                    (javax.inject.Provider<?>) getFieldValue(fieldInfo);
                return provider.get();
              }
            });
      } else {
        javax.inject.Provider<?> fieldValueUnsafe =
            (javax.inject.Provider<?>) getFieldValue(fieldInfo);
        binderUnsafe.toProvider(fieldValueUnsafe);
      }
    } else if (fieldInfo.bindAnnotation.lazy()) {
      binderUnsafe.toProvider(
          new Provider<Object>() {
            @Override
            // @Nullable
            public Object get() {
              return getFieldValue(fieldInfo);
            }
          });
    } else {
      Object fieldValue = getFieldValue(fieldInfo);
      if (fieldValue == null) {
        binderUnsafe.toProvider(Providers.of(null));
      } else {
        binderUnsafe.toInstance(fieldValue);
      }
    }
  }

  // @Nullable
  /**
   * Returns the field value to bind, throwing for non-{@code @Nullable} fields with null values,
   * and for null "transparent providers".
   */
  private static Object getFieldValue(final BoundFieldInfo fieldInfo) {
    Object fieldValue = fieldInfo.getValue();
    if (fieldValue == null && !fieldInfo.allowsNull()) {
      if (isTransparentProvider(fieldInfo.fieldType.getRawType())) {
        throw new NullBoundFieldValueException(
            new Message(
                fieldInfo.field,
                "Binding to null is not allowed. Use Providers.of(null) if this is your intended "
                    + "behavior."));
      } else {
        throw new NullBoundFieldValueException(
            new Message(
                fieldInfo.field,
                "Binding to null values is only allowed for fields that are annotated @Nullable."));
      }
    }
    return fieldValue;
  }

  @Override
  public void configure(Binder binder) {
    binder = binder.skipSources(BoundFieldModule.class);

    for (Message message : deferredBindingErrors) {
      binder.addError(message);
    }

    for (BoundFieldInfo fieldInfo : boundFields) {
      try {
        bindField(binder, fieldInfo);
      } catch (NullBoundFieldValueException e) {
        // Defer errors for all eagerly bound null values
        binder.addError(e.message);
      }
    }
  }
}
