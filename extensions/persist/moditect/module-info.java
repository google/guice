module com.google.guice.extensions.persist {
	exports com.google.inject.persist;

	requires transitive com.google.guice;
	requires com.google.common;

	requires static javax.servlet.api;
	requires static aopalliance;
	requires static hibernate.jpa;

}
