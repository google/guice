module com.google.guice.extensions.assistedinject {
	requires com.google.common;
	requires com.google.guice;
	requires javax.inject;
	requires java.logging;
	exports com.google.inject.assistedinject;

	opens com.google.inject.assistedinject to com.google.guice;
}
