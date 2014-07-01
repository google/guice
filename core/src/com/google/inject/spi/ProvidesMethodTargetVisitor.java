package com.google.inject.spi;

import com.google.inject.Provides;
import com.google.inject.spi.BindingTargetVisitor;

/**
 * A visitor for the {@literal @}{@link Provides} bindings.
 * <p>
 * If your {@link BindingTargetVisitor} implements this interface, bindings created by using
 * {@code @Provides} will be visited through this interface.
 *
 * @since 4.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface ProvidesMethodTargetVisitor<T, V> extends BindingTargetVisitor<T, V> {
  
  /**
   * Visits an {@link ProvidesMethodBinding} created with an {@literal @}{@link Provides} method.
   */
  V visit(ProvidesMethodBinding<? extends T> providesMethodBinding);
}
