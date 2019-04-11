module com.google.guice.extensions.daggeradapter {
	exports com.google.inject.daggeradapter;

	requires com.google.common;
	requires com.google.guice;
	requires dagger;

	opens com.google.inject.daggeradapter to com.google.guice;
}
