module com.google.guice.extensions.spring {

	exports com.google.inject.spring;

	requires com.google.common;
	requires transitive com.google.guice;
	requires spring.beans;

	opens com.google.inject.spring to com.google.guice;
}
