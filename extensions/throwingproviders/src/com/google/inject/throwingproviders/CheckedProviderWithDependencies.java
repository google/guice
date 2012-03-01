// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.throwingproviders;

import com.google.inject.spi.HasDependencies;
import com.google.inject.throwingproviders.ThrowingProviderBinder.SecondaryBinder;

/**
 * A checked provider with dependencies, so {@link HasDependencies} can be implemented
 * when using the {@link SecondaryBinder#using} methods.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
interface CheckedProviderWithDependencies<T> extends CheckedProvider<T>, HasDependencies {

}
