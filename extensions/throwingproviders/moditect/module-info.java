module com.google.guice.extensions.throwingproviders {

	exports com.google.inject.throwingproviders;

	requires transitive com.google.guice;

	requires com.google.common;
	requires java.logging;

	requires static jsr305;

}
