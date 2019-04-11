module com.google.guice.extensions.servlet {
	exports com.google.inject.servlet;

	requires com.google.common;

	//Servlet 3.1
	//requires static javax.servlet.api;
	//Servlet 2.5
	requires static servlet.api;
	requires com.google.guice;

	requires java.logging;
	requires javax.inject;

	opens com.google.inject.servlet to com.google.guice;
}
