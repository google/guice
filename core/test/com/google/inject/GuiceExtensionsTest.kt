package com.google.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.inject.Qualifier
import kotlin.reflect.full.primaryConstructor

@RunWith(JUnit4::class)
class GuiceExtensionsTest {

  @Qualifier
  annotation class MyAnnotation

  interface Foo

  class FooImpl : Foo

  @Test
  fun testTypeLiteral() {
    assertThat(typeLiteral<List<String>>()).isEqualTo(object : TypeLiteral<List<String>>() {})
    assertThat(typeLiteral<String?>()).isEqualTo(object : TypeLiteral<String?>() {})
  }

  @Test
  fun testKey() {
    assertThat(key<List<String>>()).isEqualTo(object : Key<List<String>>() {})
    assertThat(key<String?>()).isEqualTo(object : Key<String?>() {})
  }

  @Test
  fun testKeyPassingAnnotation() {
    assertThat(key<List<String>>(MyAnnotation::class))
      .isEqualTo(object : Key<List<String>>(MyAnnotation::class.java) {})
    assertThat(key<String?>(MyAnnotation::class))
      .isEqualTo(object : Key<String?>(MyAnnotation::class.java) {})
  }

  @Test
  fun testKeyPassingAnnotationInstance() {
    val annotation: MyAnnotation = MyAnnotation::class.primaryConstructor!!.call()
    assertThat(key<List<String>>(annotation))
      .isEqualTo(object : Key<List<String>>(annotation) {})
    assertThat(key<String?>(annotation))
      .isEqualTo(object : Key<String?>(annotation) {})
  }

  @Test
  fun testOfType() {
    val key: Key<String> = object : Key<String>(MyAnnotation::class.java) {}
    assertThat(key.ofType<Foo>()).isEqualTo(object : Key<Foo>(MyAnnotation::class.java) {})
    assertThat(key.ofType<Foo?>()).isEqualTo(object : Key<Foo?>(MyAnnotation::class.java) {})
  }

  @Test
  fun testWithAnnotation() {
    assertThat(object : Key<Foo>() {}.withAnnotation(MyAnnotation::class))
      .isEqualTo(object : Key<Foo>(MyAnnotation::class.java) {})
    assertThat(object : Key<Foo?>() {}.withAnnotation(MyAnnotation::class))
      .isEqualTo(object : Key<Foo?>(MyAnnotation::class.java) {})
  }

  @Test
  fun testLinkedBindingBuilderTo() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(object : Key<Foo?>() {}).to<FooImpl>()
        }
      })
    val foo = injector.getInstance(object : Key<Foo?>() {})
    assertThat(foo).isInstanceOf(FooImpl::class.java)
  }

  @Test
  fun testLinkedBindingBuilderToWithAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(object : Key<FooImpl>(MyAnnotation::class.java) {}).toInstance(FooImpl())
          bind(object : Key<Foo?>() {}).to<FooImpl>(MyAnnotation::class)
        }
      })
    val foo: Foo? = injector.getInstance(object : Key<Foo?>() {})
    assertThat(foo).isInstanceOf(FooImpl::class.java)
  }

  @Test
  fun testBindToMissingTypePointsToItself() {
    val module = object : AbstractModule() {
      override fun configure() {
        // The next line compiles even though the extension method 'to' does not specify a type!
        // Kotlin's type-inference mechanism treats the line as if it were:
        //   bind<Foo>.to<Foo>()
        // which binds Foo to itself!
        bind(Foo::class.java).to()
      }
    }

    val e: CreationException =
      assertThrows(CreationException::class.java) {
        Guice.createInjector(module)
      }
    assertThat(e).hasMessageThat().contains("Binding points to itself")
  }

  @Test
  fun testInScope() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(Foo::class.java).to(FooImpl::class.java).inScope<Singleton>()
        }
      })
    val foo1: Foo = injector.getInstance(Foo::class.java)
    assertThat(foo1).isInstanceOf(FooImpl::class.java)
    val foo2 = injector.getInstance(Foo::class.java)
    assertThat(foo2).isInstanceOf(FooImpl::class.java)
    assertThat(foo1).isSameInstanceAs(foo2)
  }

  @Test
  fun testInjectorGetInstance() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(object : Key<Foo>() {}).to(FooImpl::class.java)
          bind(object : Key<String?>() {}).toProvider(Provider { null })
        }
      })
    val foo1: Foo = injector.getInstance() // Type inference magic
    assertThat(foo1).isInstanceOf(FooImpl::class.java)
    val foo2 = injector.getInstance<Foo>() // Or specify the type this way
    assertThat(foo2).isInstanceOf(FooImpl::class.java)
    assertThat(foo1).isNotSameInstanceAs(foo2)

    assertThat(injector.getInstance<String?>()).isNull()
  }

  @Test
  fun testInjectorGetInstanceWithAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(object : Key<Foo>(MyAnnotation::class.java) {}).to(FooImpl::class.java)
          bind(object : Key<String?>(MyAnnotation::class.java) {}).toProvider(Provider { null })
        }
      })
    val foo1: Foo = injector.getInstance(MyAnnotation::class) // Type inference magic
    assertThat(foo1).isInstanceOf(FooImpl::class.java)
    val foo2 = injector.getInstance<Foo>(MyAnnotation::class) // Or specify the type this way
    assertThat(foo2).isInstanceOf(FooImpl::class.java)
    assertThat(foo1).isNotSameInstanceAs(foo2)

    assertThat(injector.getInstance<String?>(MyAnnotation::class)).isNull()
  }

  @Test
  fun testInjectorGetProvider() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(Foo::class.java).to(FooImpl::class.java)
        }
      })
    val foo: Provider<Foo?> = injector.getProvider() // Type inference magic
    assertThat(foo.get()).isInstanceOf(FooImpl::class.java)
  }

  @Test
  fun testInjectorGetProviderWithAnnotation() {
    val injector =
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind(object : Key<Foo>(MyAnnotation::class.java) {}).to(FooImpl::class.java)
        }
      })
    val foo: Provider<Foo?> = injector.getProvider(MyAnnotation::class) // Type inference magic
    assertThat(foo.get()).isInstanceOf(FooImpl::class.java)
  }
}