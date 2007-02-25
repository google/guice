package com.google.inject.binder;

/**
 * Specifies the attributes of a binding.
 */
public interface BindingBuilder<T> extends BindingAnnotationBuilder<T>,
    BindingImplementationBuilder<T>, BindingScopeBuilder {

}
