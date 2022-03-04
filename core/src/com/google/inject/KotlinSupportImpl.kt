package com.google.inject

import com.google.inject.internal.Annotations
import com.google.inject.internal.Errors
import com.google.inject.internal.KotlinSupportInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.function.Predicate
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.kotlinProperty

/**
 * Singleton object that contains functions to inspect Kotlin code.
 *
 * While this is private, it is accessed reflectively by [com.google.inject.internal.KotlinSupport].
 */
private object KotlinSupportImpl : KotlinSupportInterface {

  private val NO_ANNOTATIONS: Array<Annotation> = arrayOf()
  private val FALSE_PREDICATE = Predicate<Int> { false }

  override fun getAnnotations(field: Field): Array<Annotation> {
    return if (field.declaringClass.isKotlinClass) {
      field.kotlinProperty?.annotations?.toTypedArray() ?: NO_ANNOTATIONS
    } else {
      NO_ANNOTATIONS
    }
  }

  override fun isNullable(field: Field): Boolean {
    return if (field.declaringClass.isKotlinClass) {
      field.kotlinProperty?.returnType?.isMarkedNullable ?: false
    } else {
      false
    }
  }

  override fun getIsParameterKotlinNullablePredicate(constructor: Constructor<*>): Predicate<Int> {
    if (!constructor.declaringClass.isKotlinClass) {
      return FALSE_PREDICATE
    }
    val kFunction = constructor.kotlinFunction ?: return FALSE_PREDICATE
    return Predicate { index: Int -> kFunction.parameters[index].type.isMarkedNullable }
  }

  override fun getIsParameterKotlinNullablePredicate(method: Method): Predicate<Int> {
    if (!method.declaringClass.isKotlinClass) {
      return Predicate<Int> { false }
    }
    val kFunction = method.kotlinFunction ?: return Predicate<Int> { false }
    // Note: Non-static methods have a 'this' parameter at index zero, so the index needs to be
    // incremented for that case.
    val indexOffset = if (Modifier.isStatic(method.modifiers)) 0 else 1
    return Predicate { index: Int ->
      val offsettedIndex = index + indexOffset
      if (offsettedIndex == kFunction.parameters.size && kFunction.isSuspend) {
        // The Continuation object at the end of a suspend function is not visible to the Kotlin
        // reflection library so don't invoke `kFunction.parameters[offsettedIndex]` below.
        // Instead, just return false.
        false
      } else {
        kFunction.parameters[offsettedIndex].type.isMarkedNullable
      }
    }
  }

  override fun checkConstructorParameterAnnotations(constructor: Constructor<*>, errors: Errors) {
    if (!constructor.declaringClass.isKotlinClass) return

    val parameters = constructor.kotlinFunction?.parameters ?: return
    val propertiesByName: Map<String, KProperty1<out Any, *>> =
      constructor.declaringClass.kotlin.memberProperties.associateBy { it.name }
    for (parameterIndex in parameters.indices) {
      val parameter: KParameter = parameters[parameterIndex]
      val property: KProperty1<*, *> = propertiesByName[parameter.name] ?: continue
      val bindingAnnotation = property.annotations.find {
        Annotations.isBindingAnnotation(it.annotationClass.java)
      } ?: continue
      val bindingAnnotationHasAtTargetMissingParameter =
        bindingAnnotation.annotationClass.annotations.filterIsInstance<Target>().any { target ->
          !target.allowedTargets.contains(AnnotationTarget.VALUE_PARAMETER)
        }
      if (bindingAnnotationHasAtTargetMissingParameter) {
        errors.atTargetIsMissingParameter(
          bindingAnnotation,
          parameter.name,
          constructor.declaringClass
        )
      }
    }
  }

  override fun isLocalClass(clazz: Class<*>): Boolean {
    return clazz.isKotlinClass && clazz.kotlin.qualifiedName == null
  }

  private val Class<*>.isKotlinClass: Boolean
    get() = this.getDeclaredAnnotation(Metadata::class.java) != null
}
