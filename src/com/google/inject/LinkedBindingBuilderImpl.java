package com.google.inject;

import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.spi.SourceProviders;

/**
 * Links one binding to another.
 */
class LinkedBindingBuilderImpl<T> implements LinkedBindingBuilder<T> {

  final Key<T> key;
  Key<? extends T> destination;
  Object source = SourceProviders.UNKNOWN_SOURCE;
  private BinderImpl binder;

  LinkedBindingBuilderImpl(BinderImpl binder, Key<T> key) {
    this.binder = binder;
    this.key = key;
  }

  Object getSource() {
    return source;
  }

  Key<T> getKey() {
    return key;
  }

  Key<? extends T> getDestination() {
    return destination;
  }

  LinkedBindingBuilderImpl<T> from(Object source) {
    this.source = source;
    return this;
  }

  public void to(Key<? extends T> destination) {
    if (this.destination != null) {
      binder.addError(source, ErrorMessages.LINK_DESTINATION_ALREADY_SET);
    } else {
      this.destination = destination;
    }
  }

  public void to(Class<? extends T> destination) {
    to(Key.get(destination));
  }

  public void to(TypeLiteral<? extends T> destination) {
    to(Key.get(destination));
  }
}
