// Prevent instantiation from Java
@file:JvmName("-GuiceExtensions")

package com.google.inject

import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import kotlin.reflect.KClass

// Functions to create a TypeLiteral and Key

/**
 * Returns a new [TypeLiteral] of [T].
 *
 * Usage: `val myTypeLiteral : TypeLiteral<T> = typeLiteral<String>()`
 *
 * @param T the type argument to be passed to [TypeLiteral]
 * @sample [GuiceExtensionsTest.testTypeLiteral]
 */
inline fun <reified T> typeLiteral(): TypeLiteral<T> = object : TypeLiteral<T>() {}

/**
 * Return a new [Key] of [T].
 *
 * Usage (no annotation): `val myKey : Key<String> = key<String>()`
 *
 * Usage (with annotation): `val myAnnotatedKey: Key<String> = key<String>(MyAnnotation::class)`
 *
 * @param T the type argument to be passed to [Key]
 * @param annotation the annotation class to annotate the returned Key. When null (the default), the
 * [Key] will not be annotated.
 * @sample [GuiceExtensionsTest.testKey]
 * @sample [GuiceExtensionsTest.testKeyPassingAnnotation]
 */
inline fun <reified T> key(annotation: KClass<out Annotation>? = null): Key<T> =
  if (annotation == null) object : Key<T>() {} else object : Key<T>(annotation.java) {}

/**
 * Returns a new [Key] of [T].
 *
 * Usage: `val myKey : Key<String> = key<String>(myAnnotationInstance)`
 *
 * @param T the type argument to be passed to [Key]
 * @param annotation the annotation instance to annotate the returned [Key]
 * @sample [GuiceExtensionsTest.testKeyPassingAnnotationInstance]
 */
inline fun <reified T> key(annotation: Annotation): Key<T> = object : Key<T>(annotation) {}

/**
 * Returns a new [Key] of [T], using the same annotation (if present) as the given key.
 *
 * Usage: `val myKey : Key<String> = myOtherKey.ofType<String>()`
 *
 * @param T the type argument to be passed to [Key]
 * @sample [GuiceExtensionsTest.testOfType]
 *
 * @return a new `Key<T>` with the same annotation (if any) from the source key
 */
inline fun <reified T> Key<*>.ofType(): Key<T> = ofType(typeLiteral<T>())

/**
 * Return a new [Key], whose type is the same but whose annotation is the given annotation.
 *
 * Usage: `val myKey : Key<String> = myKeyOfString.withAnnotation(MyAnnotation::class)`
 *
 * @param annotation the annotation class used to annotate the returned [Key]
 * @sample [GuiceExtensionsTest.testWithAnnotation]
 */
fun <T, A : Annotation> Key<T>.withAnnotation(annotation: KClass<A>): Key<T> =
  withAnnotation(annotation.java)

// Extensions for LinkedBindingBuilder.

/**
 * An extension function of [LinkedBindingBuilder] that returns a [ScopedBindingBuilder].
 *
 * WARNING: Make sure to pass a [T] to this function. If you don't, you will end up binding your key
 * to itself (see [GuiceExtensionsTest.testBindToMissingTypePointsToItself]).
 *
 * Usage (no annotation): `bind(...).to<MyImplementation>()`
 *
 * Usage (with annotation): `bind(...).to<MyImplementation>(MyAnnotation::class)`
 *
 * @param T the type argument to be passed to the binding target's [Key]
 * @param annotation the annotation class used to annotate the binding target's [Key]. When null
 * (the default), the target's key will have no annotation.
 * @sample [GuiceExtensionsTest.testLinkedBindingBuilderTo]
 * @sample [GuiceExtensionsTest.testLinkedBindingBuilderToWithAnnotation]
 */
inline fun <reified T> LinkedBindingBuilder<in T>.to(
  annotation: KClass<out Annotation>? = null
): ScopedBindingBuilder = to(key<T>(annotation))

// Note: There is no LinkedBindingBuilder<T>.toProvider() extension function because it would
// require having two type parameters:
//   inline fun <reified T, reified S : javax.inject.Provider<T>>
//     LinkedBindingBuilder<T>.toProvider(): ScopedBindingBuilder =
//       this.toProvider(S::class.java)
// That would cause users to repeat the type (ugly):
//   bind<MyType>().toProvider<MyType, MyTypeProvider>()
// Instead, use the inline function ExtendedLinkedBindingBuilder.toProvider or call
//   toProvider(key<MyTypeProvider>())

// Extensions for ScopedBindingBuilder

/**
 * An extension function of [ScopedBindingBuilder] that allows you to specify the scope of a binding
 * without needing to use backticks.
 *
 * Usage: `bind(...).to(...).inScope<Singleton>()`
 *
 * @param A the [Scope]'s annotation
 * @sample [GuiceExtensionsTest.testInScope]
 */
inline fun <reified A : Annotation> ScopedBindingBuilder.inScope() = this.`in`(A::class.java)

// Extensions for Injector

/**
 * Returns an instance of [T] from the [Injector].
 *
 * Usage (no annotation): `val s : String = injector.getInstance<String>()`
 *
 * Usage (with annotation): `val s : String = injector.getInstance<String>(MyAnnotation::class)`
 *
 * @param T the type of the [Key] to get
 * @param annotation the annotation class of the [Key] to get. When null (the default), this will
 * get an instance of [T] whose [Key] is not annotated.
 * @sample [GuiceExtensionsTest.testInjectorGetInstance]
 * @sample [GuiceExtensionsTest.testInjectorGetInstanceWithAnnotation]
 *
 * @return the requested (possibly-annotated) [T]
 */
inline fun <reified T> Injector.getInstance(annotation: KClass<out Annotation>? = null): T =
  getInstance(key<T>(annotation))

/**
 * Returns a [Provider] of [T] from the [Injector].
 *
 * Usage (no annotation): `val s : Provider<String> = injector.getProvider<String>() Usage (with
 * annotation) `val s : Provider<String> = injector.getProvider<String>(MyAnnotation::class)`
 *
 * @param T the type of the returned [Provider]'s [Key]
 * @param annotation the annotation class of the returned [Provider]'s [Key]. When null (the
 * default), this will return a [Provider] whose [Key] is not annotated.
 * @sample [GuiceExtensionsTest.testInjectorGetProvider]
 * @sample [GuiceExtensionsTest.testInjectorGetProviderWithAnnotation]
 *
 * @return the requested (possibly-annotated) [Provider] of [T]
 */
inline fun <reified T> Injector.getProvider(
  annotation: KClass<out Annotation>? = null
): Provider<T> = getProvider(key<T>(annotation))

// Extensions for AbstractModule

/**
 * Returns an [AnnotatedBindingBuilder] for the given type.
 *
 * Usage: `bind<MyInterface>().to<...>()`
 *
 * @param T the type to bind
 * @sample [AbstractModuleExtensionsTest.testBind]
 */
inline fun <reified T> AbstractModule.bind(): ExtendedAnnotatedBindingBuilder<T> =
  `access$ExtendedAnnotatedBindingBuilderConstructor`(`access$binder`().bind(typeLiteral<T>()))

/**
 * Returns an [ExtendedLinkedBindingBuilder] for the given type and annotation.
 *
 * Usage: `bind<MyInterface>(MyAnnotation::class).to<...>()`
 *
 * @param T the bound [Key]'s type
 * @param annotation the bound [Key]'s annotation
 * @sample [AbstractModuleExtensionsTest.testBindPassingAnnotation]
 */
inline fun <reified T> AbstractModule.bind(
  annotation: KClass<out Annotation>
): ExtendedLinkedBindingBuilder<T> =
  `access$ExtendedLinkedBindingBuilderConstructor`(`access$binder`().bind(key<T>(annotation)))

/**
 * Returns a [Multibinder] of [T].
 *
 * Usage (no annotation): `setBinder<String>().addBinding().to(...)`
 *
 * Usage (with annotation): `setBinder<String>(MyAnnotation::class).addBinding().to(...)`
 *
 * @param T the type of the [Multibinder]
 * @param annotation the [Multibinder]'s annotation (if non-null)
 * @sample [AbstractModuleExtensionsTest.testSetBinder]
 * @sample [AbstractModuleExtensionsTest.testSetBinderPassingAnnotation]
 */
inline fun <reified T : Any> AbstractModule.setBinder(
  annotation: KClass<out Annotation>? = null
): Multibinder<T> = Multibinder.newSetBinder(`access$binder`(), key<T>(annotation))

/**
 * Returns a [MapBinder] of [K] and [V].
 *
 * Usage (no annotation): `mapBinder<String, MyInterface>().addBinding(...).to(...)`
 *
 * Usage (with annotation): `mapBinder<String,
 * MyInterface>(MyAnnotation::class).addBinding(...).to(...)`
 *
 * @param K the [MapBinder]'s key type
 * @param V The [MapBinder]'s value type
 * @param annotation the [MapBinder]'s annotation (if non-null)
 * @sample [AbstractModuleExtensionsTest.testMapBinder]
 * @sample [AbstractModuleExtensionsTest.testMapBinderPassingAnnotation]
 */
inline fun <reified K : Any, reified V : Any> AbstractModule.mapBinder(
  annotation: KClass<out Annotation>? = null
): MapBinder<K, V> =
  if (annotation == null) {
    MapBinder.newMapBinder(`access$binder`(), typeLiteral<K>(), typeLiteral<V>())
  } else {
    MapBinder.newMapBinder(`access$binder`(), typeLiteral<K>(), typeLiteral<V>(), annotation.java)
  }

/**
 * Returns a [Provider] of [T].
 *
 * Usage (no annotation): `getProvider<String>()`
 *
 * Usage (with annotation): `getProvider<String>(MyAnnotation::class)`
 *
 * @param T the [Provider]'s type
 * @param annotation the annotation for the [Provider]'s [Key]
 * @sample [AbstractModuleExtensionsTest.testGetProvider]
 */
inline fun <reified T> AbstractModule.getProvider(
  annotation: KClass<out Annotation>? = null
): Provider<T> = `access$binder`().getProvider(key<T>(annotation))

@PublishedApi // needed to access the protected binder() in a protected inline function.
internal fun AbstractModule.`access$binder`(): Binder = binder()

@PublishedApi // needed to access an internal constructor from a protected inline function.
internal fun <T> `access$ExtendedLinkedBindingBuilderConstructor`(
  delegate: LinkedBindingBuilder<T>
): ExtendedLinkedBindingBuilder<T> = ExtendedLinkedBindingBuilder(delegate)

@PublishedApi // needed to access an internal constructor from a protected inline function.
internal fun <T> `access$ExtendedAnnotatedBindingBuilderConstructor`(
  delegate: AnnotatedBindingBuilder<T>
): ExtendedAnnotatedBindingBuilder<T> = ExtendedAnnotatedBindingBuilder(delegate)
