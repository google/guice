# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
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
    name = "request-scoped-annotation",
    srcs = ["RequestScoped.java"],
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    deps = [
        "//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "servlet",
    srcs = glob(
        ["*.java"],
        exclude = ["RequestScoped.java"],
    ),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    tags = ["maven_coordinates=com.google.inject.extensions:guice-servlet:" + POM_VERSION],
    exports = [":request-scoped-annotation"],
    deps = [
        ":request-scoped-annotation",
        "//core/src/com/google/inject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/escape",
        "//third_party/java/guava/net",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-servlet",
    artifact_name = "Google Guice - Extensions - Servlet",
    artifact_target = ":servlet",
    artifact_target_libs = [
        ":request-scoped-annotation",
    ],
    is_extension = True,
    javadoc_srcs = [":javadoc-srcs"],
)
