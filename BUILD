load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

package(default_visibility = ["//visibility:public"])

package_group(
    name = "src",
    packages = ["//..."],
)

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
        "//extensions/service/src/com/google/inject/service:javadoc-srcs",
        "//extensions/servlet/src/com/google/inject/servlet:javadoc-srcs",
        "//extensions/spring/src/com/google/inject/spring:javadoc-srcs",
        "//extensions/struts2/src/com/google/inject/struts2:javadoc-srcs",
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder:javadoc-srcs",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders:javadoc-srcs",
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders:javadoc-srcs",
    ],
    doctitle = "Guice Dependency Injection API",
    external_javadoc_links = [
        "https://google.github.io/guava/releases/30.1-jre/api/docs/",
        "https://google.github.io/truth/api/0.45/",
        "http://errorprone.info/api/latest/",
        "https://tomcat.apache.org/tomcat-5.5-doc/servletapi/",
        "http://aopalliance.sourceforge.net/doc/",
    ],
    deps = [
        "//core/src/com/google/inject",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",
        "//extensions/dagger-adapter/src/com/google/inject/daggeradapter",
        "//extensions/grapher/src/com/google/inject/grapher",
        "//extensions/jmx/src/com/google/inject/tools/jmx",
        "//extensions/jndi/src/com/google/inject/jndi",
        "//extensions/persist/src/com/google/inject/persist",
        "//extensions/service/src/com/google/inject/service",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//extensions/spring/src/com/google/inject/spring",
        "//extensions/struts2/src/com/google/inject/struts2",
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders",
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders",
    ],
)
