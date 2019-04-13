module com.google.guice.extensions.throwingproviders {

	exports com.google.inject.throwingproviders;

	requires transitive com.google.guice;

	requires com.google.common;
	requires java.logging;
	requires javax.inject;

	requires static jsr305;

	opens com.google.inject.throwingproviders to com.google.guice;
}
