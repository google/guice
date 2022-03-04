package com.google.inject

import com.google.common.truth.Truth.assertThat
import com.google.inject.testing.fieldbinder.Bind
import com.google.inject.testing.fieldbinder.BoundFieldModule
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KotlinNullabilityTest {

  private class NullablePropertyContainer @Inject constructor(val i: Int, val s: String?)

  private class MethodInjectedNullablePropertyContainer {
    var i: Int = 0
    var s: String? = ""

    @Inject
    fun inject(i: Int, s: String?) {
      this.i = i
      this.s = s
    }
  }

  private class SuspendMethodInjectedNullablePropertyContainer {
    var i: Int = 0
    var s: String? = ""

    @Inject
    suspend fun inject(i: Int, s: String?) {
      this.i = i
      this.s = s
    }
  }

  // Note: Despite all attempts to make the companion's inject() static, the Kotlin reflection
  // code will still see inject() as a non-static method!
  private class NullablePropertyCompanionContainer {
    companion object {
      var i: Int = 0
      var s: String? = ""

      @JvmStatic
      @Inject
      fun inject(i: Int, s: String?) {
        this.i = i
        this.s = s
      }
    }
  }

  private class NullableInjectedFieldContainer {
    @Inject var s: String? = ""
  }

  @Test
  fun testNullableBindingIsProvided() {
    val value = "value"
    val injector = Guice.createInjector(object : AbstractModule() {
      override fun configure() {
        bind<String>().toInstance(value)
        bind<Int>().toInstance(0)
        bind<Continuation<Unit>>().toInstance(Continuation(Dispatchers.Default) {})
        requestStaticInjection(classForStaticInjection)
      }
    })
    injector.injectMembers(NullablePropertyCompanionContainer.Companion)

    assertThat(injector.getInstance<NullablePropertyContainer>().s).isEqualTo(value)
    assertThat(injector.getInstance<MethodInjectedNullablePropertyContainer>().s).isEqualTo(value)
    assertThat(injector.getInstance<SuspendMethodInjectedNullablePropertyContainer>().s)
      .isEqualTo(value)
    assertThat(injector.getInstance<NullableInjectedFieldContainer>().s).isEqualTo(value)
    assertThat(NullablePropertyCompanionContainer.s).isEqualTo(value)
    assertThat(nullableProperty).isEqualTo(value)
  }

  @Test
  fun testNullableBindingIsBoundToNull() {
    val injector = Guice.createInjector(object : AbstractModule() {
      override fun configure() {
        bind<String>().toProvider(Provider { null })
        bind<Int>().toInstance(0)
        bind<Continuation<Unit>>().toInstance(Continuation(Dispatchers.Default) {})
        requestStaticInjection(classForStaticInjection)
      }
    })
    injector.injectMembers(NullablePropertyCompanionContainer.Companion)

    assertThat(injector.getInstance<NullablePropertyContainer>().s).isNull()
    assertThat(injector.getInstance<MethodInjectedNullablePropertyContainer>().s).isNull()
    assertThat(injector.getInstance<SuspendMethodInjectedNullablePropertyContainer>().s).isNull()
    assertThat(injector.getInstance<NullableInjectedFieldContainer>().s).isNull()
    assertThat(NullablePropertyCompanionContainer.s).isNull()
    assertThat(nullableProperty).isNull()
  }

  class NullablePropertyContainerWithBind(@Bind val s: String? = null)

  @Test
  fun testNullableBindingWithBoundFieldModule() {
    val instance = NullablePropertyContainerWithBind()
    val value = Guice.createInjector(BoundFieldModule.of(instance)).getInstance(String::class.java)
    assertThat(value).isNull()
  }

  @Test
  fun testNonNullableBindingIsBoundToNull() {
    val injector = Guice.createInjector(object : AbstractModule() {
      override fun configure() {
        bind<String>().toProvider(Provider { null })
        bind<Int>().toProvider(Provider { null })
        bind<Continuation<Unit>>().toInstance(Continuation(Dispatchers.Default) {})
      }
    })

    assertThrowsNullInjectedIntoNonNullable<ProvisionException> {
      injector.getInstance<NullablePropertyContainer>()
    }
    assertThrowsNullInjectedIntoNonNullable<ProvisionException> {
      injector.getInstance<MethodInjectedNullablePropertyContainer>()
    }
    assertThrowsNullInjectedIntoNonNullable<ProvisionException> {
      injector.getInstance<SuspendMethodInjectedNullablePropertyContainer>()
    }
    assertThrowsNullInjectedIntoNonNullable<ProvisionException> {
      injector.injectMembers(NullablePropertyCompanionContainer.Companion)
    }
  }

  @Test
  fun testNonNullableIsBoundToNull_staticInjection() {
    assertThrowsNullInjectedIntoNonNullable<CreationException> {
      Guice.createInjector(object : AbstractModule() {
        override fun configure() {
          bind<String>().toProvider(Provider { null })
          bind<Int>().toProvider(Provider { null })
          requestStaticInjection(classForStaticInjection)
        }
      })
    }
  }

  private inline fun <reified T : Throwable> assertThrowsNullInjectedIntoNonNullable(
    block: () -> Unit
  ) {
    val exception = assertFailsWith<T> { block() }
    assertThat(exception).hasMessageThat().contains("NULL_INJECTED_INTO_NON_NULLABLE")
    assertThat(exception).hasMessageThat().contains("parameter i")
    assertThat(exception).hasMessageThat().doesNotContain("parameter s")
  }
}

// This holds the java class containing top-level functions.
private val classForStaticInjection: Class<*> = object : Any() {}::class.java.enclosingClass
private var nullableProperty: String? = ""

@Inject
private fun inject(i: Int, s: String?) {
  nullableProperty = s
}
