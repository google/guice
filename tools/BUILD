load("@rules_java//java:defs.bzl", "java_binary")

package(
    default_visibility = ["//:src"],
)

java_binary(
    name = "osgi_wrapper",
    srcs = ["OsgiWrapper.java"],
    main_class = "com.google.inject.tools.OsgiWrapper",
    deps = [
        "//third_party/java/bnd:bndlib",
        "//third_party/java/guava/io",
        "//third_party/java/picocli",
    ],
)
