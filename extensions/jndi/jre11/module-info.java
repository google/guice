module com.google.guice.extensions.jndi {
	exports com.google.inject.jndi;

	requires com.google.guice;
	requires java.naming;
	requires javax.inject;

	opens com.google.inject.jndi to com.google.guice;
}
