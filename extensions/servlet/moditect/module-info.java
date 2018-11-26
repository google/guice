module com.google.guice.extensions.servlet {
	exports com.google.inject.servlet;

	requires com.google.common;
	requires javax.servlet.api;

	requires transitive com.google.guice;
	requires java.logging;

}
