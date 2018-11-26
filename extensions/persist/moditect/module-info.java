module com.google.guice.extensions.persist {
	exports com.google.inject.persist;

	requires com.google.guice;
	requires com.google.common;

	requires static javax.servlet.api;
	requires static aopalliance;
	requires static hibernate.jpa;

	opens com.google.inject.persist to com.google.guice;
	opens com.google.inject.persist.finder to com.google.guice;
	opens com.google.inject.persist.jpa to com.google.guice;
}
