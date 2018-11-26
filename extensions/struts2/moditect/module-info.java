module com.google.guice.extensions.struts2 {
	exports com.google.inject.struts2;

	requires transitive com.google.guice;
	requires transitive com.google.guice.extensions.servlet;

	requires static xwork.core;
	requires java.logging;

}
