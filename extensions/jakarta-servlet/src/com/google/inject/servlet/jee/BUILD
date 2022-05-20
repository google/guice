# Copyright 2022 Google Inc.  All rights reserved.
load("@rules_java//java:defs.bzl", "java_library")
load("//:mvn.bzl", "gen_maven_artifact")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
    "POM_VERSION",
)

package(
    default_visibility = ["//:src"],
)

java_library(
    name = "jakarta-request-scoped-annotation",
    srcs = ["RequestScoped.java"],
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    deps = [
        "//third_party/java/jakarta_inject",
    ],
)

java_library(
    name = "jee",
    srcs = glob(
        ["*.java"],
        exclude = ["RequestScoped.java"],
    ),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    tags = ["maven_coordinates=com.google.inject.extensions:guice-jakarta-servlet:" + POM_VERSION],
    exports = [":jakarta-request-scoped-annotation"],
    deps = [
        ":jakarta-request-scoped-annotation",
        "//core/src/com/google/inject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/escape",
        "//third_party/java/guava/net",
        "//third_party/java/jakarta_inject",
        "//third_party/java/servlet/jakarta_servlet_api",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-jakarta-servlet",
    artifact_name = "Google Guice - Extensions - JEE Servlet",
    artifact_target = ":jee",
    artifact_target_libs = [
        ":jakarta-request-scoped-annotation",
    ],
    is_extension = True,
    javadoc_srcs = [":javadoc-srcs"],
)
