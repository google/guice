package com.google.inject.persist.utils;

import static java.util.function.Function.identity;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.spi.Element;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeConverterBinding;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

public class PersistenceInjectorResource extends SuiteAndTestResource implements Injector {

  private Injector injector;

  private PersistService persistService;

  private boolean autoStart = true;

  private final String unitName;
  private final Function<JpaPersistModule, JpaPersistModule> configurer;

  public PersistenceInjectorResource(String unitName) {
    this(unitName, identity());
  }

  public PersistenceInjectorResource(String unitName,
                                     Function<JpaPersistModule, JpaPersistModule> configurer) {
    this(Lifecycle.SUITE, unitName, configurer);
  }

  public PersistenceInjectorResource(Lifecycle lifecycle, String unitName,
                                     Function<JpaPersistModule, JpaPersistModule> configurer) {
    super(lifecycle);
    this.unitName = unitName;
    this.configurer = configurer;
  }

  public PersistenceInjectorResource autoStart(boolean autoStart) {
    this.autoStart = autoStart;
    return this;
  }

  @Override
  protected void beforeTest() {
    if (autoStart) {
      persistService = injector.getInstance(PersistService.class);
      persistService.start();
    }
  }

  @Override
  protected void beforeSuite() {
    injector = Guice.createInjector(configurer.apply(new JpaPersistModule(unitName)));
  }

  @Override
  protected void afterTest() {
    try {
      persistService.stop();
    } catch (Exception ignored) {
    }

    try {
      injector.getInstance(EntityManager.class).close();
    } catch (Exception ignored) {
    }

    try {
      injector.getInstance(EntityManagerFactory.class).close();
    } catch (Exception ignored) {
    }
  }

  @Override
  protected void afterSuite() {

  }

  // methods delegated to injector

  @Override
  public void injectMembers(Object instance) {
    injector.injectMembers(instance);
  }

  @Override
  public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
    return injector.getMembersInjector(typeLiteral);
  }

  @Override
  public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
    return injector.getMembersInjector(type);
  }

  @Override
  public Map<Key<?>, Binding<?>> getBindings() {
    return injector.getBindings();
  }

  @Override
  public Map<Key<?>, Binding<?>> getAllBindings() {
    return injector.getAllBindings();
  }

  @Override
  public <T> Binding<T> getBinding(Key<T> key) {
    return injector.getBinding(key);
  }

  @Override
  public <T> Binding<T> getBinding(Class<T> type) {
    return injector.getBinding(type);
  }

  @Override
  public <T> Binding<T> getExistingBinding(Key<T> key) {
    return injector.getExistingBinding(key);
  }

  @Override
  public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
    return injector.findBindingsByType(type);
  }

  @Override
  public <T> Provider<T> getProvider(Key<T> key) {
    return injector.getProvider(key);
  }

  @Override
  public <T> Provider<T> getProvider(Class<T> type) {
    return injector.getProvider(type);
  }

  @Override
  public <T> T getInstance(Key<T> key) {
    return injector.getInstance(key);
  }

  @Override
  public <T> T getInstance(Class<T> instanceType) {
    return injector.getInstance(instanceType);
  }

  @Override
  public Injector getParent() {
    return injector.getParent();
  }

  @Override
  public Injector createChildInjector(Iterable<? extends Module> modules) {
    return injector.createChildInjector(modules);
  }

  @Override
  public Injector createChildInjector(Module... modules) {
    return injector.createChildInjector(modules);
  }

  @Override
  public Map<Class<? extends Annotation>, Scope> getScopeBindings() {
    return injector.getScopeBindings();
  }

  @Override
  public Set<TypeConverterBinding> getTypeConverterBindings() {
    return injector.getTypeConverterBindings();
  }

  @Override
  public List<Element> getElements() {
    return injector.getElements();
  }

  @Override
  public Map<TypeLiteral<?>, List<InjectionPoint>> getAllMembersInjectorInjectionPoints() {
    return injector.getAllMembersInjectorInjectionPoints();
  }
}
