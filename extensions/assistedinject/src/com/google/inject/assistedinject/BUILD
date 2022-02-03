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
    name = "assistedinject",
    srcs = glob(["**/*.java"]),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    tags = ["maven_coordinates=com.google.inject.extensions:guice-assitedinject:" + POM_VERSION],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-assistedinject",
    artifact_name = "Google Guice - Extensions - AssistedInject",
    artifact_target = ":assistedinject",
    is_extension = True,
    javadoc_srcs = [":javadoc-srcs"],
)
