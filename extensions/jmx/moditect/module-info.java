module com.google.guice.extensions.jmx {
	exports com.google.inject.tools.jmx;

	requires transitive com.google.guice;
	requires java.management;

}
