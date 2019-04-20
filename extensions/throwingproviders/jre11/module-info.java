module com.google.guice.extensions.throwingproviders {

	exports com.google.inject.throwingproviders;

	requires transitive com.google.guice;

	requires java.logging;
	requires com.google.common;
	requires javax.inject;
	requires jsr305;

	opens com.google.inject.throwingproviders to com.google.guice;
}
