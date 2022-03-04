package com.google.inject

import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import kotlin.reflect.KClass

/**
 * A wrapper of [AnnotatedBindingBuilder] that provides convenience functions when using
 * [KAbstractModule].
 */
@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
class ExtendedAnnotatedBindingBuilder<T>
internal constructor(private val delegate: AnnotatedBindingBuilder<T>) :
  ExtendedLinkedBindingBuilder<T>(delegate), AnnotatedBindingBuilder<T> by delegate {

  /**
   * Returns a [LinkedBindingBuilder] of [T] where the bound key is annotated with the given
   * annotation class.
   *
   * Usage:
   *   `bind<...>().annotatedWith(MyAnnotation::class).to<...>()`
   *
   * @param annotation the annotation to annotate [T]
   * @sample [KAbstractModuleTest.testExtendedAnnotatedBindingBuilder_annotatedWith]
   */
  fun <A : Annotation> annotatedWith(annotation: KClass<A>): LinkedBindingBuilder<T> =
    annotatedWith(annotation.java)
}
