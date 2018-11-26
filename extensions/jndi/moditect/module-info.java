module com.google.guice.extensions.jndi {
	exports com.google.inject.jndi;

	requires transitive com.google.guice;
	requires transitive java.naming;

}
