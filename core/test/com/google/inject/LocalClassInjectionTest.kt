package com.google.inject

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LocalClassInjectionTest {
  @Test
  fun testInjectLocalClassWithExternalReference() {
    val externalReference = 42

    class LocalClass @Inject constructor() {
      @Suppress("unused")
      private fun existsToReferToExternalReference() = externalReference
    }

    val ex = assertFailsWith<ConfigurationException> {
      Guice.createInjector().getInstance<LocalClass>()
    }
    assertThat(ex).hasMessageThat().contains(
      "Injecting into local classes is not supported.  Please use a non-local class instead of " +
        "LocalClassInjectionTest\$testInjectLocalClassWithExternalReference\$LocalClass"
    )
  }

  @Test
  fun testInjectLocalClassWithNoExternalReference() {
    class LocalClass @Inject constructor()

    val ex = assertFailsWith<ConfigurationException> {
      Guice.createInjector().getInstance<LocalClass>()
    }
    assertThat(ex).hasMessageThat().contains(
      "Injecting into local classes is not supported.  Please use a non-local class instead of " +
        "LocalClassInjectionTest\$testInjectLocalClassWithNoExternalReference\$LocalClass"
    )
  }
}
