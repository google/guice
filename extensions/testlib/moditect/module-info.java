module com.google.guice.extensions.testlib {
	requires com.google.common;
	requires truth;
	requires com.google.guice.extensions.throwingproviders;

	requires static jsr305;

	requires transitive com.google.guice;
	requires javax.inject;


}
