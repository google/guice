module com.google.guice.extensions.persist {
	exports com.google.inject.persist;

	requires transitive com.google.guice;

	requires static javax.servlet.api;
	requires aopalliance;
	requires hibernate.jpa;
	requires com.google.common;
}
