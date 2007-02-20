package com.google.inject.binder;

import com.google.inject.Factory;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import java.lang.annotation.Annotation;

/**
 * Specifies the attributes of a binding.
 */
public interface BindingBuilder<T> extends BindingAnnotationBuilder<T>,
    BindingImplementationBuilder<T>, BindingScopeBuilder {

}
