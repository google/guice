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
class MembersInjectorImpl<T> implements MembersInjector<T> {

  static <T> MembersInjectorImpl<T> create(
      InjectorImpl injector,
      TypeLiteral<T> typeLiteral,
      EncounterImpl<T> encounter,
      ImmutableList<SingleMemberInjector> memberInjectors) {
    memberInjectors = memberInjectors.isEmpty() ? null : memberInjectors;
    var userMembersInjectors =
        encounter.getMembersInjectors().isEmpty() ? null : encounter.getMembersInjectors().asList();
    var injectionListeners =
        encounter.getInjectionListeners().isEmpty()
            ? null
            : encounter.getInjectionListeners().asList();
    var addedAspects =
        (InternalFlags.isBytecodeGenEnabled() && !encounter.getAspects().isEmpty())
            ? encounter.getAspects()
            : null;
    if (InternalFlags.getUseMethodHandlesOption()) {
      return new MethodHandleMembersInjectorImpl<>(
          injector,
          typeLiteral,
          memberInjectors,
          userMembersInjectors,
          injectionListeners,
          addedAspects);
    }
    return new MembersInjectorImpl<>(
        injector,
        typeLiteral,
        memberInjectors,
        userMembersInjectors,
        injectionListeners,
        addedAspects);
  }

  protected final TypeLiteral<T> typeLiteral;
  protected final InjectorImpl injector;
  // a null list means empty. Since it is common for many of these lists to be empty we can save
  // some memory lookups by representing empty as null.
  @Nullable protected final ImmutableList<SingleMemberInjector> memberInjectors;
  @Nullable protected final ImmutableList<MembersInjector<? super T>> userMembersInjectors;
  @Nullable protected final ImmutableList<InjectionListener<? super T>> injectionListeners;
  @Nullable protected final ImmutableList<MethodAspect> addedAspects;

  private MembersInjectorImpl(
      InjectorImpl injector,
      TypeLiteral<T> typeLiteral,
      ImmutableList<SingleMemberInjector> memberInjectors,
      ImmutableList<MembersInjector<? super T>> userMembersInjectors,
      ImmutableList<InjectionListener<? super T>> injectionListeners,
      ImmutableList<MethodAspect> addedAspects) {
    this.injector = injector;
    this.typeLiteral = typeLiteral;
    this.memberInjectors = memberInjectors;
    this.userMembersInjectors = userMembersInjectors;
    this.injectionListeners = injectionListeners;
    this.addedAspects = addedAspects;
  }

  public ImmutableList<SingleMemberInjector> getMemberInjectors() {
    return memberInjectors == null ? ImmutableList.<SingleMemberInjector>of() : memberInjectors;
  }

  @Override
  public void injectMembers(T instance) {
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

  protected void notifyListeners(T instance) throws InternalProvisionException {
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

  protected void doInjectMembers(T t, InternalContext context, boolean toolableOnly)
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

  /** A specialized implementation that is implemented in terms of MethodHandles. */
  static final class MethodHandleMembersInjectorImpl<T> extends MembersInjectorImpl<T> {

    // Lazily allocated and initialized.
    private volatile MethodHandle doInjectHandle;
    private volatile MethodHandle notifyListenersHandle;
    private volatile MethodHandle injectMembersAndNotifyListenersHandle;

    private MethodHandleMembersInjectorImpl(
        InjectorImpl injector,
        TypeLiteral<T> typeLiteral,
        ImmutableList<SingleMemberInjector> memberInjectors,
        ImmutableList<MembersInjector<? super T>> userMembersInjectors,
        ImmutableList<InjectionListener<? super T>> injectionListeners,
        ImmutableList<MethodAspect> addedAspects) {
      super(
          injector,
          typeLiteral,
          memberInjectors,
          userMembersInjectors,
          injectionListeners,
          addedAspects);
    }

    @Override
    protected void injectMembers(T instance, InternalContext ctx)
        throws InternalProvisionException {
      try {
        getInjectMembersAndNotifyListenersHandle(null).invokeExact(instance, ctx);
      } catch (InternalProvisionException ipe) {
        throw ipe;
      } catch (Throwable t) {
        throw InternalMethodHandles.sneakyThrow(t);
      }
    }

    @Override
    protected void notifyListeners(T instance) throws InternalProvisionException {
      try {
        getNotifyListenersHandle().invokeExact((Object) instance);
      } catch (InternalProvisionException ipe) {
        throw ipe;
      } catch (Throwable t) {
        throw InternalMethodHandles.sneakyThrow(t);
      }
    }

    @Override
    protected void doInjectMembers(T instance, InternalContext context, boolean toolableOnly)
        throws InternalProvisionException {
      try {
        getDoInjectHandle(null).invokeExact((Object) instance, context, toolableOnly);
      } catch (InternalProvisionException ipe) {
        throw ipe;
      } catch (Throwable t) {
        throw InternalMethodHandles.sneakyThrow(t);
      }
    }

    /**
     * Returns a method handle that injects all members. The signature is {@code (T,
     * InternalContext) -> void}
     *
     * <p>This produces a handle that is equivalent to calling `injectMembers(instance, context,
     * false)`.
     */
    MethodHandle getInjectMembersAndNotifyListenersHandle(@Nullable LinkageContext linkageContext) {
      var local = injectMembersAndNotifyListenersHandle;
      if (local != null) {
        return local;
      }
      // Set `toolableOnly` to false
      // (Object, InternalContext)->void
      var injectMembers =
          MethodHandles.insertArguments(getDoInjectHandle(linkageContext), 2, false);

      // (Object, InternalContext)->void
      var notifyListeners =
          MethodHandles.dropArguments(getNotifyListenersHandle(), 1, InternalContext.class);

      local = MethodHandles.foldArguments(notifyListeners, injectMembers);

      synchronized (this) {
        var race = injectMembersAndNotifyListenersHandle;
        if (race == null) {
          injectMembersAndNotifyListenersHandle = local;
        } else {
          local = race;
        }
      }
      return local;
    }

    private static final MethodHandle DO_NOTHING_INJECT =
        MethodHandles.empty(
            methodType(void.class, Object.class, InternalContext.class, boolean.class));

    private static final MethodHandle TEST_TOOLABLE_ONLY =
        MethodHandles.dropArguments(
            MethodHandles.identity(boolean.class), 0, Object.class, InternalContext.class);

    /**
     * Returns a handle with the siganture (Object, InternalContext, boolean)->void that injects all
     * the members.
     */
    private MethodHandle getDoInjectHandle(@Nullable LinkageContext linkageContext) {
      var local = doInjectHandle;
      if (local != null) {
        return local;
      }

      if (linkageContext == null) {
        linkageContext = new LinkageContext();
      }
      if (this.memberInjectors != null) {
        for (SingleMemberInjector injector : memberInjectors) {
          var injectHandle = injector.getInjectHandle(linkageContext);
          injectHandle = MethodHandles.dropArguments(injectHandle, 2, boolean.class);
          // If it is toolable we can always inject it, but if not we need to skip it if
          // toolableonly is set
          if (!injector.getInjectionPoint().isToolable()) {
            injectHandle =
                MethodHandles.guardWithTest(TEST_TOOLABLE_ONLY, DO_NOTHING_INJECT, injectHandle);
          }
          if (local == null) {
            local = injectHandle;
          } else {
            local = MethodHandles.foldArguments(injectHandle, local);
          }
        }
      }
      if (userMembersInjectors != null) {
        // (Object)->void
        MethodHandle userMembersInjectorsHandle = null;

        for (MembersInjector<? super T> injector : userMembersInjectors) {
          // (Object)->void
          var userHandle = INJECT_MEMBERS_HANDLE.bindTo(injector);
          // Wrap it in a try..catch.
          // Catch RuntimeException and rethrow as InternalProvisionException.errorInUserInjector
          // (RuntimeException)->InternalProvisionException
          var rethrow =
              MethodHandles.insertArguments(
                  ERROR_IN_USER_INJECTOR_HANDLE, 0, injector, typeLiteral);
          // Throw that exception.
          // (RuntimeException)->void
          rethrow =
              MethodHandles.filterArguments(
                  MethodHandles.throwException(void.class, InternalProvisionException.class),
                  0,
                  rethrow);
          // Catch any exceptions and rethrow it.
          userHandle = MethodHandles.catchException(userHandle, RuntimeException.class, rethrow);

          // merge with the previous one if necessary
          if (userMembersInjectorsHandle == null) {
            userMembersInjectorsHandle = userHandle;
          } else {
            userMembersInjectorsHandle =
                MethodHandles.foldArguments(userHandle, userMembersInjectorsHandle);
          }
        }
        var test =
            MethodHandles.dropArguments(
                MethodHandles.identity(boolean.class), 0, Object.class, InternalContext.class);
        var ifToolableOnly =
            MethodHandles.empty(
                methodType(void.class, Object.class, InternalContext.class, boolean.class));
        var ifNotToolableOnly =
            MethodHandles.dropArguments(
                userMembersInjectorsHandle, 1, InternalContext.class, boolean.class);
        userMembersInjectorsHandle =
            MethodHandles.guardWithTest(test, ifToolableOnly, ifNotToolableOnly);

        if (local == null) {
          local = userMembersInjectorsHandle;
        } else {
          local = MethodHandles.foldArguments(userMembersInjectorsHandle, local);
        }
      }
      // there must be no injectors at all
      if (local == null) {
        local = DO_NOTHING_INJECT;
      }

      // update the cache
      synchronized (this) {
        // See if we lost a race
        var raceValue = doInjectHandle;
        if (raceValue == null) {
          doInjectHandle = local;
        } else {
          local = raceValue;
        }
      }
      return local;
    }

    /**
     * Returns a handle with the signature (Object instance)->void that invokes all the listeners.
     *
     * <p>Catches exceptions and rethrows them as InternalProvisionException.
     */
    private MethodHandle getNotifyListenersHandle() {
      var local = notifyListenersHandle;
      if (local != null) {
        return local;
      }
      // Now notify listeners.
      if (injectionListeners != null) {
        for (InjectionListener<? super T> listener : injectionListeners) {
          // (Object)->void
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
          // Execute prior and then this listener.
          if (local == null) {
            local = listenerHandle;
          } else {
            local = MethodHandles.foldArguments(listenerHandle, local);
          }
        }
      } else {
        local = MethodHandles.empty(methodType(void.class, Object.class));
      }

      // update the cache
      synchronized (this) {
        // See if we lost a race
        var raceValue = notifyListenersHandle;
        if (raceValue == null) {
          notifyListenersHandle = local;
        } else {
          local = raceValue;
        }
      }

      return local;
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
  }
}
