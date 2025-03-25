/*
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.internal;

import static com.google.inject.internal.InternalMethodHandles.findStaticOrDie;
import static com.google.inject.internal.InternalMethodHandles.findVirtualOrDie;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.MembersInjector;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.InjectionPoint;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import javax.annotation.Nullable;

/**
 * Injects members of instances of a given type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class MembersInjectorImpl<T> implements MembersInjector<T> {
  private final TypeLiteral<T> typeLiteral;
  private final InjectorImpl injector;
  // a null list means empty. Since it is common for many of these lists to be empty we can save
  // some memory lookups by representing empty as null.
  @Nullable private final ImmutableList<SingleMemberInjector> memberInjectors;
  @Nullable private final ImmutableList<MembersInjector<? super T>> userMembersInjectors;
  @Nullable private final ImmutableList<InjectionListener<? super T>> injectionListeners;
  @Nullable private final ImmutableList<MethodAspect> addedAspects;

  MembersInjectorImpl(
      InjectorImpl injector,
      TypeLiteral<T> typeLiteral,
      EncounterImpl<T> encounter,
      ImmutableList<SingleMemberInjector> memberInjectors) {
    this.injector = injector;
    this.typeLiteral = typeLiteral;
    this.memberInjectors = memberInjectors.isEmpty() ? null : memberInjectors;
    this.userMembersInjectors =
        encounter.getMembersInjectors().isEmpty() ? null : encounter.getMembersInjectors().asList();
    this.injectionListeners =
        encounter.getInjectionListeners().isEmpty()
            ? null
            : encounter.getInjectionListeners().asList();
    this.addedAspects =
        (InternalFlags.isBytecodeGenEnabled() && !encounter.getAspects().isEmpty())
            ? encounter.getAspects()
            : null;
  }

  public ImmutableList<SingleMemberInjector> getMemberInjectors() {
    return memberInjectors == null ? ImmutableList.<SingleMemberInjector>of() : memberInjectors;
  }

  @Override
  public void injectMembers(T instance) {
    // TODO(b/366058184): investigate a methodhandle version of this to support explicit members
    // injection requests.
    if (instance == null) {
      return;
    }
    try (InternalContext context = injector.enterContext()) {
      injectMembers(instance, context);
    } catch (InternalProvisionException ipe) {
      throw ipe.addSource(typeLiteral).toProvisionException();
    }
  }

  // Exposed for use in the constructor injector.
  void injectMembers(T instance, InternalContext context) throws InternalProvisionException {
    doInjectMembers(instance, context, /* toolableOnly= */ false);
    notifyListeners(instance);
  }

  // Exposed for use in the Initializer for `toInstance` bindings.
  void injectAndNotify(
      InternalContext context,
      final T instance,
      @Nullable final ProvisionListenerStackCallback<T> provisionCallback, // possibly null!
      final Object source,
      final boolean toolableOnly)
      throws InternalProvisionException {
    if (provisionCallback != null) {
      provisionCallback.provision(
          context,
          /* dependency= */ null, // not used
          (ctx, dep) -> {
            doInjectMembers(instance, ctx, toolableOnly);
            return instance;
          });
    } else {
      doInjectMembers(instance, context, toolableOnly);
    }

    // TODO: We *could* notify listeners too here,
    // but it's not clear if we want to.  There's no way to know
    // if a MembersInjector from the usersMemberInjector list wants
    // toolable injections, so do we really want to notify
    // about injection?  (We could take a strategy of only notifying
    // if atleast one InjectionPoint was toolable, in which case
    // the above callInContext could return 'true' if it injected
    // anything.)
    if (!toolableOnly) {
      notifyListeners(instance);
    }
  }

  private void notifyListeners(T instance) throws InternalProvisionException {
    ImmutableList<InjectionListener<? super T>> localInjectionListeners = injectionListeners;
    if (localInjectionListeners == null) {
      // no listeners
      return;
    }
    // optimization: use manual for/each to save allocating an iterator here
    for (int i = 0; i < localInjectionListeners.size(); i++) {
      InjectionListener<? super T> injectionListener = localInjectionListeners.get(i);
      try {
        injectionListener.afterInjection(instance);
      } catch (RuntimeException e) {
        throw InternalProvisionException.errorNotifyingInjectionListener(
            injectionListener, typeLiteral, e);
      }
    }
  }

  /**
   * Returns a method handle that injects all members. The signature is {@code (T, InternalContext)
   * -> void}
   */
  MethodHandle getInjectMembersAndNotifyListenersHandle(LinkageContext linkageContext) {
    var handle = MethodHandles.empty(methodType(void.class, Object.class, InternalContext.class));
    if (memberInjectors != null) {
      for (SingleMemberInjector injector : memberInjectors) {
        handle = MethodHandles.foldArguments(injector.getInjectHandle(linkageContext), handle);
      }
    }
    if (userMembersInjectors != null) {
      for (MembersInjector<? super T> injector : userMembersInjectors) {
        var userHandle = INJECT_MEMBERS_HANDLE.bindTo(injector);
        // Wrap it in a try..catch.
        // Catch RuntimeException and rethrow as InternalProvisionException.errorInUserInjector
        // (RuntimeException)->InternalProvisionException
        var rethrow =
            MethodHandles.insertArguments(ERROR_IN_USER_INJECTOR_HANDLE, 0, injector, typeLiteral);
        // Throw that exception.
        // (RuntimeException)->void
        rethrow =
            MethodHandles.filterArguments(
                MethodHandles.throwException(void.class, InternalProvisionException.class),
                0,
                rethrow);
        // Catch any exceptions and rethrow it.
        userHandle = MethodHandles.catchException(userHandle, RuntimeException.class, rethrow);
        // match the expected signature of the method.
        userHandle = MethodHandles.dropArguments(userHandle, 1, InternalContext.class);
        // Execute prior and then this listener.
        handle = MethodHandles.foldArguments(userHandle, handle);
      }
    }

    // Now notify listeners.
    if (injectionListeners != null) {
      for (InjectionListener<? super T> listener : injectionListeners) {
        var listenerHandle = AFTER_INJECTION_HANDLE.bindTo(listener);
        // Wrap it in a try..catch.
        // Catch RuntimeException and rethrow as
        // InternalProvisionException.errorNotifyingInjectionListener
        // (RuntimeException)->InternalProvisionException
        var rethrow =
            MethodHandles.insertArguments(
                ERROR_NOTIFYING_INJECTION_LISTENER_HANDLE, 0, listener, typeLiteral);
        // (RuntimeException)->void
        rethrow =
            MethodHandles.filterArguments(
                MethodHandles.throwException(void.class, InternalProvisionException.class),
                0,
                rethrow);

        listenerHandle =
            MethodHandles.catchException(listenerHandle, RuntimeException.class, rethrow);
        // match the expected signature of the method.
        listenerHandle = MethodHandles.dropArguments(listenerHandle, 1, InternalContext.class);
        // Execute prior and then this listener.
        handle = MethodHandles.foldArguments(listenerHandle, handle);
      }
    }

    return handle;
  }

  private static final MethodHandle INJECT_MEMBERS_HANDLE =
      findVirtualOrDie(
          MembersInjector.class, "injectMembers", methodType(void.class, Object.class));

  private static final MethodHandle AFTER_INJECTION_HANDLE =
      findVirtualOrDie(
          InjectionListener.class, "afterInjection", methodType(void.class, Object.class));

  private static final MethodHandle ERROR_IN_USER_INJECTOR_HANDLE =
      findStaticOrDie(
          InternalProvisionException.class,
          "errorInUserInjector",
          methodType(
              InternalProvisionException.class,
              MembersInjector.class,
              TypeLiteral.class,
              RuntimeException.class));
  private static final MethodHandle ERROR_NOTIFYING_INJECTION_LISTENER_HANDLE =
      findStaticOrDie(
          InternalProvisionException.class,
          "errorNotifyingInjectionListener",
          methodType(
              InternalProvisionException.class,
              InjectionListener.class,
              TypeLiteral.class,
              RuntimeException.class));

  private void doInjectMembers(T t, InternalContext context, boolean toolableOnly)
      throws InternalProvisionException {
    ImmutableList<SingleMemberInjector> localMembersInjectors = memberInjectors;
    if (localMembersInjectors != null) {
      // optimization: use manual for/each to save allocating an iterator here
      for (int i = 0, size = localMembersInjectors.size(); i < size; i++) {
        SingleMemberInjector injector = localMembersInjectors.get(i);
        if (!toolableOnly || injector.getInjectionPoint().isToolable()) {
          injector.inject(context, t);
        }
      }
    }

    // TODO: There's no way to know if a user's MembersInjector wants toolable injections.
    if (!toolableOnly) {
      ImmutableList<MembersInjector<? super T>> localUsersMembersInjectors = userMembersInjectors;
      if (localUsersMembersInjectors != null) {
        // optimization: use manual for/each to save allocating an iterator here
        for (int i = 0; i < localUsersMembersInjectors.size(); i++) {
          MembersInjector<? super T> userMembersInjector = localUsersMembersInjectors.get(i);
          try {
            userMembersInjector.injectMembers(t);
          } catch (RuntimeException e) {
            throw InternalProvisionException.errorInUserInjector(
                userMembersInjector, typeLiteral, e);
          }
        }
      }
    }
  }

  /**
   * Returns true if a call to {@link #injectMembers} and {@link #notifyListeners} would have no
   * effect.
   */
  boolean isEmpty() {
    return memberInjectors == null && userMembersInjectors == null && injectionListeners == null;
  }

  @Override
  public String toString() {
    return "MembersInjector<" + typeLiteral + ">";
  }

  public ImmutableSet<InjectionPoint> getInjectionPoints() {
    ImmutableList<SingleMemberInjector> localMemberInjectors = memberInjectors;
    if (localMemberInjectors != null) {
      ImmutableSet.Builder<InjectionPoint> builder = ImmutableSet.builder();
      for (SingleMemberInjector memberInjector : localMemberInjectors) {
        builder.add(memberInjector.getInjectionPoint());
      }
      return builder.build();
    }
    return ImmutableSet.of();
  }

  public ImmutableList<MethodAspect> getAddedAspects() {
    return addedAspects == null ? ImmutableList.<MethodAspect>of() : addedAspects;
  }
}
