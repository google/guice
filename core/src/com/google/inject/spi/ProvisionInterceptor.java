package com.google.inject.spi;

import com.google.inject.Binding;
import com.google.inject.Provider;
import com.google.inject.internal.ProvisionCallback;

/**
 * Intercepts actual instantiations and allows you to override them
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 4.2
 */
public interface ProvisionInterceptor extends ProvisionListener {
  /**
   * This method allows you to hijack the actual provision logic
   *
   * <p>You must not call {@link Provider#get()} on the provider returned by {@link
   * Binding#getProvider}, otherwise you will get confusing error messages.
   *
   * @param binding * Returns the Binding this is provisioning.
   * @param result result of previous provisioning, probably in case it's not null you should return it without modification
   * @param callable callback allowing you to invoke the actual instantiation. You
   */
  <T> T interceptProvision(Binding<T> binding, T result,
                           ProvisionCallback<T> callable);
}
