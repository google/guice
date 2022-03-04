package com.google.inject

import com.google.common.truth.Truth.assertThat
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.inject.Qualifier

@RunWith(JUnit4::class)
class AbstractModuleExtensionsTest {

  @Qualifier
  annotation class MyAnnotation

  interface Foo

  class FooImpl : Foo

  class FooProvider : Provider<Foo> {
    override fun get() = FooImpl()
  }

  // Below each example is the equivalent code without using the :guice-ktx library.
  @Test
  @Suppress("UNUSED_VARIABLE", "RemoveExplicitTypeArguments")
  fun overview() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          val fooTypeLiteral: TypeLiteral<Foo> = typeLiteral<Foo>()
          // val fooTypeLiteral: TypeLiteral<Foo> = object : TypeLiteral<Foo>() {}

          val fooKey: Key<Foo> = key<Foo>()
          // val fooKey: Key<Foo> = object : Key<Foo>() {}

          val annotatedFooKey: Key<Foo> = key<Foo>(MyAnnotation::class)
          // val annotatedFooKey: Key<Foo> = object : Key<Foo>(MyAnnotation::class.java)

          val stringKey: Key<String> = annotatedFooKey.ofType<String>()
          // val stringKey: Key<String> = annotatedFooKey.ofType(String::class.java)

          bind<Foo>().to<FooImpl>().inScope<Singleton>()
          // bind(Foo::class.java).to(FooImpl::class.java).`in`(Singleton::class.java)

          bind<Foo>(MyAnnotation::class).toProvider<FooProvider>()
          // bind(object : Key<Foo>(MyAnnotation::class.java){}).toProvider(FooProvider::class.java)

          bind<String>().annotatedWith(MyAnnotation::class).toInstance("MyAnnotation")
          // bind(String::class.java).annotatedWith(MyAnnotation::class.java).toInstance("MyAnnotation")

          val fooProvider: Provider<Foo> = getProvider<Foo>()
          // val fooProvider : Provider<Foo> = getProvider(Foo::class.java)

          val setBinder: Multibinder<Foo> = setBinder<Foo>()
          // val setBinder = Multibinder.newSetBinder(binder(), Foo::class.java)

          setBinder.addBinding().to<Foo>()
          // setBinder.addBinding().to(object : Key<Foo>() {})

          val mapBinder: MapBinder<String, Foo> = mapBinder<String, Foo>()
          // val mapBinder = MapBinder.newMapBinder(binder(), String::class.java, Foo::class.java)

          mapBinder.addBinding("a").to<Foo>()
          // mapBinder.addBinding("a").to(object : Key<Foo>())
        }
      })

    val set: Set<Foo> = injector.getInstance<Set<@JvmSuppressWildcards Foo>>()
    // The Guice key for setProvider is actually Key<Set<? extends Foo>> due to Kotlin adding
    // wildcards, but fortunately Multibinder provides an alias of Key<Set<? extends Foo>> to
    // Key<Set<Foo>>.
    val setProvider: Provider<Set<Foo>> = injector.getProvider<Set<Foo>>()
    assertThat(set).hasSize(1)
    assertThat(set).isEqualTo(setProvider.get())

    val map: Map<String, Foo> = injector.getInstance<Map<String, Foo>>()
    // The Guice key for mapProvider is actually Key<Map<String, ? extends Foo>> due to Kotlin
    // adding wildcards, but fortunately Multibinder provides an alias of
    // Key<Map<String, ? extends Foo>> to Key<Map<String, Foo>>.
    val mapProvider: Provider<Map<String, Foo>> = injector.getProvider<Map<String, Foo>>()
    assertThat(map).hasSize(1)
    assertThat(map).isEqualTo(mapProvider.get())
  }

  @Test
  @Suppress("UNUSED_VARIABLE")
  fun overviewWithTypeInference() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          // Note: The following does NOT work (i.e. no type inference on anonymous subclassing)
          // val fooTypeLiteral: TypeLiteral<Foo> = object : TypeLiteral()
          val fooTypeLiteral: TypeLiteral<Foo> = typeLiteral()
          val fooKey: Key<Foo> = key()
          val annotatedFooKey: Key<Foo> = key(MyAnnotation::class)
          val stringKey: Key<String> = annotatedFooKey.ofType()

          bind<Foo>().to<FooImpl>().inScope<Singleton>()
          bind<Foo>(MyAnnotation::class).toProvider<FooProvider>()
          bind<String>().annotatedWith(MyAnnotation::class).toInstance("MyAnnotation")
          val fooProvider: Provider<Foo> = getProvider()

          val setBinder: Multibinder<Foo> = setBinder()
          setBinder.addBinding().to() // adds a binding to Key<Foo>

          val mapBinder: MapBinder<String, Foo> = mapBinder()
          mapBinder.addBinding("a").to() // adds a binding to Key<Foo>
        }
      })

    val set: Set<Foo> = injector.getInstance()
    assertThat(set).hasSize(1)

    val map: Map<String, Foo> = injector.getInstance()
    assertThat(map).hasSize(1)
  }

  // Note: The remaining methods test each inline function in isolation

  @Test
  fun testBind() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<Foo>().to(FooImpl::class.java)
          bind<String?>().toProvider(Provider { null })
        }
      })
    assertThat(injector.getInstance(Foo::class.java)).isInstanceOf(FooImpl::class.java)
    assertThat(injector.getInstance(object : Key<String?>() {})).isNull()
  }

  @Test
  fun testBindWithParameterizedType() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<List<String>>().toInstance(listOf("a", "b"))
        }
      })
    val foo: List<String>? = injector.getInstance(object : Key<List<String>>() {})
    assertThat(foo).containsExactly("a", "b")
  }

  @Test
  fun testBindPassingAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<Foo>(MyAnnotation::class).toInstance(FooImpl())
          bind<String?>(MyAnnotation::class).toProvider(Provider { null })
        }
      })
    assertThat(injector.getInstance(object : Key<Foo>(MyAnnotation::class.java) {}))
      .isInstanceOf(FooImpl::class.java)
    assertThat(injector.getInstance(object : Key<String?>(MyAnnotation::class.java) {})).isNull()
  }

  @Test
  fun testExtendedLinkedBindingBuilder_toProvider() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<Foo>().toProvider<FooProvider>()
        }
      })
    val foo = injector.getInstance(object : Key<Foo>() {})
    assertThat(foo).isInstanceOf(FooImpl::class.java)
  }

  @Test
  fun testExtendedLinkedBindingBuilder_toProviderWithAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<FooProvider>(MyAnnotation::class).toInstance(FooProvider())
          bind<Foo>().toProvider<FooProvider>(MyAnnotation::class)
        }
      })
    val foo = injector.getInstance(object : Key<Foo>() {})
    assertThat(foo).isInstanceOf(FooImpl::class.java)
  }

  @Test
  fun testExtendedAnnotatedBindingBuilder_annotatedWith() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<Foo>().annotatedWith(MyAnnotation::class).to(FooImpl::class.java)
        }
      })
    val foo = injector.getInstance(object : Key<Foo>(MyAnnotation::class.java) {})
    assertThat(foo).isInstanceOf(FooImpl::class.java)
  }

  @Test
  fun testSetBinder() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          val stringBinder: Multibinder<String> = setBinder() // Type-inference magic
          stringBinder.addBinding().toInstance("a")
          stringBinder.addBinding().toInstance("b")
        }
      })
    val strings: Set<String> =
      injector.getInstance(object : Key<Set<@JvmSuppressWildcards String>>() {})
    assertThat(strings).containsExactly("a", "b")
  }

  @Test
  fun testSetBinderPassingAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          val stringBinder: Multibinder<String> = setBinder<String>(MyAnnotation::class)
          stringBinder.addBinding().toInstance("a")
          stringBinder.addBinding().toInstance("b")
        }
      })
    val key = object : Key<Set<@JvmSuppressWildcards String>>(MyAnnotation::class.java) {}
    val strings: Set<String> = injector.getInstance(key)
    assertThat(strings).containsExactly("a", "b")
  }

  @Test
  fun testMapBinder() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          val mapBinder: MapBinder<String, Int> = mapBinder() // Type-inference magic
          mapBinder.addBinding("1").toInstance(1)
          mapBinder.addBinding("2").toInstance(2)
        }
      })
    val map: Map<String, Int> =
      injector.getInstance(object : Key<Map<String, Int>>() {})
    assertThat(map).containsExactly("1", 1, "2", 2)
  }

  @Test
  fun testMapBinderPassingAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          val mapBinder: MapBinder<String, Int> =
            mapBinder(MyAnnotation::class) // Type-inference magic
          mapBinder.addBinding("1").toInstance(1)
          mapBinder.addBinding("2").toInstance(2)
        }
      })
    val map: Map<String, Int> =
      injector.getInstance(object : Key<Map<String, Int>>(MyAnnotation::class.java) {})
    assertThat(map).containsExactly("1", 1, "2", 2)
  }

  @Test
  fun testGetProvider() {
    val annotatedFooKey: Key<Foo> = object : Key<Foo>(MyAnnotation::class.java) {}
    val annotatedStringKey: Key<String?> = object : Key<String?>(MyAnnotation::class.java) {}
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(Foo::class.java).to(FooImpl::class.java)
          bind(annotatedFooKey).toProvider(getProvider<Foo>())
          bind(object : Key<String?>() {}).toProvider(Provider { null })
          bind(annotatedStringKey).toProvider(getProvider<String?>())
        }
      })
    assertThat(injector.getInstance(annotatedFooKey)).isInstanceOf(FooImpl::class.java)
    assertThat(injector.getInstance(annotatedStringKey)).isNull()
  }

  @Test
  fun testAbstractModuleGetProviderPassingAnnotation() {
    val annotatedFooKey: Key<Foo> = object : Key<Foo>(MyAnnotation::class.java) {}
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(annotatedFooKey).to(FooImpl::class.java)
          bind(Foo::class.java).toProvider(getProvider<Foo>(MyAnnotation::class))
        }
      })
    val foo: Foo = injector.getInstance(annotatedFooKey)
    assertThat(foo).isInstanceOf(FooImpl::class.java)
  }
}
