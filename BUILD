load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

package(default_visibility = ["//visibility:public"])

package_group(
    name = "src",
    packages = ["//..."],
)

exports_files([
    "pom-template.xml",
])

javadoc_library(
    name = "javadoc",
    testonly = 1,  # some dependencies are testonly,
    srcs = [
        "//core/src/com/google/inject:javadoc-srcs",
        "//extensions/assistedinject/src/com/google/inject/assistedinject:javadoc-srcs",
        "//extensions/dagger-adapter/src/com/google/inject/daggeradapter:javadoc-srcs",
        "//extensions/grapher/src/com/google/inject/grapher:javadoc-srcs",
        "//extensions/jmx/src/com/google/inject/tools/jmx:javadoc-srcs",
        "//extensions/jndi/src/com/google/inject/jndi:javadoc-srcs",
        "//extensions/persist/src/com/google/inject/persist:javadoc-srcs",
        "//extensions/servlet/src/com/google/inject/servlet:javadoc-srcs",
        "//extensions/spring/src/com/google/inject/spring:javadoc-srcs",
        "//extensions/struts2/src/com/google/inject/struts2:javadoc-srcs",
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder:javadoc-srcs",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders:javadoc-srcs",
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders:javadoc-srcs",
    ],
    doctitle = "Guice Dependency Injection API",
    external_javadoc_links = [
        "https://docs.oracle.com/javase/8/docs/api/",
        "https://guava.dev/releases/snapshot-jre/api/docs/",
        "https://google.github.io/truth/api/latest/",
        "http://errorprone.info/api/latest/",
        "https://tomcat.apache.org/tomcat-5.5-doc/servletapi/",
        "http://aopalliance.sourceforge.net/doc/",
    ],
    groups = {
        "Guice Core": [
            "com.google.inject",
            "com.google.inject.util",
            "com.google.inject.spi",
            "com.google.inject.name",
            "com.google.inject.matcher",
            "com.google.inject.binder",
            "com.google.inject.multibindings",
        ],
        "AssistedInject Extension": ["com.google.inject.assistedinject"],
        "Dagger Adapter": ["com.google.inject.daggeradapter"],
        "Grapher Extension": [
            "com.google.inject.grapher",
            "com.google.inject.grapher.*",
        ],
        "JNDI Extension": ["com.google.inject.jndi"],
        "JMX Extension": ["com.google.inject.tools.jmx"],
        "Persist Extension": [
            "com.google.inject.persist",
            "com.google.inject.persist.*",
        ],
        "Servlet Extension": ["com.google.inject.servlet"],
        "Spring Extension": ["com.google.inject.spring"],
        "Struts2 Extension": ["com.google.inject.struts2"],
        "Test Libraries Extension": ["com.google.inject.testing.*"],
        "ThrowingProviders Extension": ["com.google.inject.throwingproviders"],
    },
    tags = ["manual"],  # Only do this when explicitly requested, not on test //...
    deps = [
        "//core/src/com/google/inject",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",
        "//extensions/dagger-adapter/src/com/google/inject/daggeradapter",
        "//extensions/grapher/src/com/google/inject/grapher",
        "//extensions/jmx/src/com/google/inject/tools/jmx",
        "//extensions/jndi/src/com/google/inject/jndi",
        "//extensions/persist/src/com/google/inject/persist",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//extensions/spring/src/com/google/inject/spring",
        "//extensions/struts2/src/com/google/inject/struts2",
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders",
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders",
    ],
)
