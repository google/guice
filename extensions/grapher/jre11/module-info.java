module com.google.guice.extensions.grapher {
	requires com.google.common;
	requires com.google.guice;
	requires java.logging;
	exports com.google.inject.grapher;

	opens com.google.inject.grapher to com.google.guice;
}
