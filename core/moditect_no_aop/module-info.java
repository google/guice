module com.google.guice {
	exports com.google.inject;
	exports com.google.inject.matcher;
	exports com.google.inject.name;
	exports com.google.inject.binder;
	exports com.google.inject.spi;
	exports com.google.inject.multibindings;

	exports com.google.inject.internal to com.google.guice.extensions.throwingproviders, com.google.guice.extensions.testlib, com.google.guice.extensions.servlet, com.google.guice.extensions.struts2, com.google.guice.extensions.grapher, com.google.guice.extensions.daggeradapter, com.google.guice.extensions.assistedinject;
	exports com.google.inject.util to com.google.guice.extensions.throwingproviders, com.google.guice.extensions.testlib, com.google.guice.extensions.assistedinject;
	exports com.google.inject.internal.util to com.google.guice.extensions.throwingproviders, com.google.guice.extensions.grapher, com.google.guice.extensions.assistedinject;


	requires com.google.common;
	requires javax.inject;
	requires java.logging;

	requires static aopalliance;
	requires static org.objectweb.asm;
	requires static cglib;
}