package com.google.inject;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.ElementType.METHOD;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import com.google.inject.util.Modules;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Set;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RestrictedBindingSource}.
 *
 * @author vzm@google.com (Vladimir Makaric)
 */
@RunWith(JUnit4.class)
public class RestrictedBindingSourceTest {

  // --------------------------------------------------------------------------
  // Core Functionality Tests
  // --------------------------------------------------------------------------

  private static final String BINDING_PERMISSION_ERROR = "Unable to bind key";
  private static final String USE_NETWORK_MODULE = "Please install NetworkModule.";
  private static final String USE_ROUTING_MODULE = "Please install RoutingModule.";
  private static final String NETWORK_ANNOTATION_IS_RESTRICTED =
      "The @Network annotation can only be used to annotate Network library Keys.";

  @RestrictedBindingSource.Permit
  @Retention(RetentionPolicy.RUNTIME)
  @interface NetworkLibrary {}

  @Qualifier
  @RestrictedBindingSource(
      explanation = USE_NETWORK_MODULE,
      permits = {NetworkLibrary.class})
  @Retention(RetentionPolicy.RUNTIME)
  @interface GatewayIpAdress {}

  @Qualifier
  @RestrictedBindingSource(
      explanation = USE_NETWORK_MODULE,
      permits = {NetworkLibrary.class})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Hostname {}

  @NetworkLibrary
  private static class NetworkModule extends AbstractModule {
    @Provides
    @GatewayIpAdress
    int provideIpAddress() {
      return 21321566;
    }

    @Override
    protected void configure() {
      bind(String.class).annotatedWith(Hostname.class).toInstance("google.com");
    }
  }

  @Test
  public void networkLibraryCanProvideItsBindings() {
    Guice.createInjector(new NetworkModule());
  }

  @RestrictedBindingSource(
      explanation = USE_ROUTING_MODULE,
      permits = {NetworkLibrary.class})
  @ImplementedBy(RoutingTableImpl.class) // For testing untargetted bindings.
  interface RoutingTable {
    int getNextHopIpAddress(int destinationIpAddress);
  }

  @NetworkLibrary
  private static class RoutingModule extends AbstractModule {
    @Provides
    RoutingTable provideRoutingTable(@GatewayIpAdress int gateway) {
      return destinationIp -> gateway;
    }
  }

  @Test
  public void networkBindingCantBeProvidedByOtherModules() {
    AbstractModule rogueModule =
        new AbstractModule() {
          // This will fail, the gateway IP can only be provided by the network library.
          @Provides
          @GatewayIpAdress
          int provideGatewayIp() {
            return 42;
          }

          @Override
          protected void configure() {
            install(new RoutingModule());
          }
        };

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
  }

  @Test
  public void twoRogueNetworkBindingsYieldTwoErrorMessages() {
    AbstractModule rogueModule =
        new AbstractModule() {
          @Provides
          @GatewayIpAdress
          int provideGatewayIp() {
            return 42;
          }

          @Provides
          RoutingTable provideRoutingTable() {
            return destinationIp -> 0;
          }
        };

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
    assertThat(expected).hasMessageThat().contains(USE_ROUTING_MODULE);
  }

  @RestrictedBindingSource.Permit
  @Retention(RetentionPolicy.RUNTIME)
  @interface NetworkTestLibrary {}

  @Qualifier
  @RestrictedBindingSource(
      explanation = USE_NETWORK_MODULE,
      permits = {NetworkLibrary.class, NetworkTestLibrary.class})
  @Retention(RetentionPolicy.RUNTIME)
  @interface MacAddress {}

  @NetworkTestLibrary
  private static class TestMacAddressModule extends AbstractModule {
    @Provides
    @MacAddress
    String provideMacAddress() {
      return "deadbeef";
    }
  }

  @Test
  public void bindingWithTwoPermitsAllowedIfOnePresent() {
    Guice.createInjector(new TestMacAddressModule());
  }

  private static class RoutingTableImpl implements RoutingTable {
    @Inject
    RoutingTableImpl() {}

    @Override
    public int getNextHopIpAddress(int destinationIpAddress) {
      return destinationIpAddress + 2;
    }
  }

  @Test
  public void untargettedBindingAllowedWithPermit() {
    @NetworkLibrary
    class PermittedNetworkModule extends AbstractModule {
      @Override
      protected void configure() {
        bind(RoutingTable.class);
      }
    }

    Guice.createInjector(new PermittedNetworkModule());
  }

  @Test
  public void untargettedBindingDisallowedWithoutPermit() {
    AbstractModule rogueModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(RoutingTable.class);
          }
        };

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_ROUTING_MODULE);
  }

  // --------------------------------------------------------------------------
  // Binder.withSource Tests
  // --------------------------------------------------------------------------

  @NetworkLibrary
  private static class PermittedModule extends AbstractModule {

    @Override
    protected void configure() {
      Method userUnpermittedModuleMethod;
      try {
        userUnpermittedModuleMethod = UnpermittedModule.class.getMethod("foo");
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }

      binder()
          .withSource(userUnpermittedModuleMethod)
          .bind(String.class)
          .annotatedWith(Hostname.class)
          .toInstance("google.com");
    }
  }

  private static class UnpermittedModule extends AbstractModule {
    public String foo() {
      return "bar";
    }
  }

  @Test
  public void permittedModuleCanWithSourceAnUnpermittedModuleMethod() {
    Guice.createInjector(new PermittedModule());
  }

  @Test
  public void unpermittedModuleCantWithSourceAPermittedModule() {
    AbstractModule rogueModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            binder()
                .withSource(PermittedModule.class)
                .bindConstant()
                .annotatedWith(GatewayIpAdress.class)
                .to(0);
          }
        };

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
  }

  // --------------------------------------------------------------------------
  // ModuleAnnotatedMethodScanner tests
  // --------------------------------------------------------------------------

  @Target(METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  private @interface NetworkProvides {}

  @Qualifier
  @RestrictedBindingSource(
      explanation = NETWORK_ANNOTATION_IS_RESTRICTED,
      permits = {NetworkLibrary.class})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Network {}

  // Adds the NetworkLibrary-owned Network annotation to keys produced by @NetworkProvides methods.
  private static class NetworkProvidesScanner extends ModuleAnnotatedMethodScanner {
    static Module module() {
      return new AbstractModule() {
        @Override
        protected void configure() {
          binder().scanModulesForAnnotatedMethods(new NetworkProvidesScanner());
        }
      };
    }

    @Override
    public String toString() {
      return "NetworkProvidesScanner";
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(NetworkProvides.class);
    }

    @Override
    public <T> Key<T> prepareMethod(
        Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
      return Key.get(key.getTypeLiteral(), Network.class);
    }
  }

  @Test
  public void rogueBindingByMethodScannerDenied() {
    AbstractModule rogueModule =
        new AbstractModule() {
          @NetworkProvides
          String provideNetworkString() {
            return "lorem ipsum";
          }
        };

    CreationException expected =
        assertThatInjectorCreationFails(rogueModule, NetworkProvidesScanner.module());

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(NETWORK_ANNOTATION_IS_RESTRICTED);
  }

  @Test
  public void bindingsAddedByMethodScannerAllowedByNetworkLib() {
    @NetworkLibrary
    class NetworkModuleWithCustomProvides extends AbstractModule {
      @NetworkProvides
      String provideNetworkString() {
        return "the real network string";
      }
    }

    Guice.createInjector(new NetworkModuleWithCustomProvides(), NetworkProvidesScanner.module());
  }

  // --------------------------------------------------------------------------
  // Modules.override tests
  // --------------------------------------------------------------------------

  @Test
  public void modulesOverrideCantOverrideRestrictedBinding() {
    Module rogueModule =
        Modules.override(new NetworkModule())
            .with(
                new AbstractModule() {
                  @Provides
                  @GatewayIpAdress
                  int provideRogueGatewayIp() {
                    return 12345;
                  }
                });

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
  }

  @Test
  public void modulesOverrideRestrictedBindingAllowedIfParentHasPermit() {
    @NetworkLibrary
    class NetworkModuleVersion2 extends AbstractModule {
      @Override
      protected void configure() {
        install(
            Modules.override(new NetworkModule())
                .with(
                    new AbstractModule() {
                      @Provides
                      @GatewayIpAdress
                      int provideGatewayIpV2() {
                        return 2;
                      }
                    }));
      }
    }

    assertThat(
            Guice.createInjector(new NetworkModuleVersion2())
                .getInstance(Key.get(Integer.class, GatewayIpAdress.class)))
        .isEqualTo(2);
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface UnrestrictedQualifier {}

  @Test
  public void modulesOverrideCanOverrideUnrestrictedBinding() {
    Module overrideModule =
        Modules.override(
                new NetworkModule(),
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bindConstant().annotatedWith(UnrestrictedQualifier.class).to("foo");
                  }
                })
            .with(
                new AbstractModule() {
                  @Provides
                  @UnrestrictedQualifier
                  String provideRogueGatewayIp() {
                    return "bar";
                  }
                });

    assertThat(
            Guice.createInjector(overrideModule)
                .getInstance(Key.get(String.class, UnrestrictedQualifier.class)))
        .isEqualTo("bar");
  }

  @Test
  public void nestedModulesOverrideCanOverrideUnrestrictedBindings() {
    Module overrideModule =
        Modules.override(
                Modules.override(
                        new NetworkModule(),
                        new AbstractModule() {
                          @Override
                          protected void configure() {
                            bindConstant().annotatedWith(UnrestrictedQualifier.class).to("foo");
                            bindConstant().annotatedWith(UnrestrictedQualifier.class).to(42);
                          }
                        })
                    .with(
                        new AbstractModule() {
                          @Provides
                          @UnrestrictedQualifier
                          String overrideString() {
                            return "bar";
                          }
                        }))
            .with(
                new AbstractModule() {
                  @Provides
                  @UnrestrictedQualifier
                  int overrideLong() {
                    return 45;
                  }
                });

    Injector injector = Guice.createInjector(overrideModule);
    assertThat(injector.getInstance(Key.get(String.class, UnrestrictedQualifier.class)))
        .isEqualTo("bar");
    assertThat(injector.getInstance(Key.get(Integer.class, UnrestrictedQualifier.class)))
        .isEqualTo(45);
  }

  @Test
  public void originalElementSourceNotTrustedOutsideModuleOverride() {
    ElementSource networkElementSource =
        (ElementSource) Elements.getElements(new NetworkModule()).get(0).getSource();

    AbstractModule rogueModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            binder()
                .withSource(networkElementSource)
                .bindConstant()
                .annotatedWith(GatewayIpAdress.class)
                .to(12);
          }
        };

    // Confirm that the original element source was spoofed.
    @SuppressWarnings("unchecked")
    Binding<Integer> rogueGatewayBinding =
        (Binding<Integer>) Elements.getElements(rogueModule).get(0);
    assertThat(((ElementSource) rogueGatewayBinding.getSource()).getOriginalElementSource())
        .isEqualTo(networkElementSource);

    // Will fail because the original element source isn't trusted, becase it wasn't set by
    // Modules.override.
    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
  }

  // --------------------------------------------------------------------------
  // PrivateModule tests
  // --------------------------------------------------------------------------

  @NetworkLibrary
  private static class NetworkModuleThatInstalls extends AbstractModule {
    final Module module;

    NetworkModuleThatInstalls(Module module) {
      this.module = module;
    }

    @Override
    protected void configure() {
      install(module);
    }
  }

  private static class PrivateModuleCreatesUnexposedNetworkBinding extends PrivateModule {
    @Override
    protected void configure() {
      bindConstant().annotatedWith(GatewayIpAdress.class).to(0);
    }
  }

  @Test
  public void parentHasPermit_childPrivateModuleCanBind() {
    Guice.createInjector(
        new NetworkModuleThatInstalls(
            // Allowed because the parent has the @NetworkLibrary permit.
            new PrivateModuleCreatesUnexposedNetworkBinding()));
  }

  @Test
  public void noPermitOnStack_privateModuleCantBind() {
    AbstractModule rogueModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            // Disallowed because there's no permit on the module stack.
            install(new PrivateModuleCreatesUnexposedNetworkBinding());
          }
        };

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
  }

  private static class PrivateModuleExposesNetworkBinding extends PrivateModule {
    @Override
    protected void configure() {
      install(
          new AbstractModule() {
            @Override
            protected void configure() {
              bindConstant().annotatedWith(GatewayIpAdress.class).to(0);
            }
          });
      expose(Key.get(Integer.class, GatewayIpAdress.class));
    }
  }

  @Test
  public void parentHasPermit_childPrivateModuleCanExposeBinding() {
    Guice.createInjector(
        new NetworkModuleThatInstalls(
            // Allowed because the parent has the @NetworkLibrary permit.
            new PrivateModuleExposesNetworkBinding()));
  }

  @Test
  public void noPermitOnStack_childPrivateModuleCantExposeBinding() {
    AbstractModule rogueModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            // Disallowed because there's no permit on the module stack.
            install(new PrivateModuleExposesNetworkBinding());
          }
        };

    CreationException expected = assertThatInjectorCreationFails(rogueModule);

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_NETWORK_MODULE);
  }

  // --------------------------------------------------------------------------
  // Child Injector tests
  // --------------------------------------------------------------------------

  @Test
  public void childInjectorCantBindRestrictedBindingWithoutPermit() {
    Injector parent = Guice.createInjector(new NetworkModule());
    AbstractModule rogueModule =
        new AbstractModule() {
          @Provides
          RoutingTable provideRoutingTable() {
            return destinationIp -> 0;
          }
        };

    CreationException expected =
        assertThrows(CreationException.class, () -> parent.createChildInjector(rogueModule));

    assertThat(expected).hasMessageThat().contains(BINDING_PERMISSION_ERROR);
    assertThat(expected).hasMessageThat().contains(USE_ROUTING_MODULE);
  }

  @Test
  public void childInjectorCanBindRestrictedBindingWithPermit() {
    Injector parent = Guice.createInjector(new NetworkModule());
    parent.createChildInjector(new RoutingModule());
  }

  CreationException assertThatInjectorCreationFails(Module... modules) {
    return assertThrows(CreationException.class, () -> Guice.createInjector(modules));
  }
}
