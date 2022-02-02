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
    name = "persist",
    srcs = glob(["**/*.java"]),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    tags = ["maven_coordinates=com.google.inject.extensions:guice-persist:" + POM_VERSION],
    deps = [
        "//core/src/com/google/inject",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//third_party/java/aopalliance",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javax_persistence",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["**/*.java"]),
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-persist",
    artifact_name = "Google Guice - Extensions - Persist",
    artifact_target = ":persist",
    is_extension = True,
    javadoc_srcs = [
        ":javadoc-srcs",
    ],
)
