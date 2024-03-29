# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
    "POM_VERSION",
)
load("//:mvn.bzl", "gen_maven_artifact")

package(
    default_visibility = ["//:src"],
)

java_library(
    name = "throwingproviders",
    srcs = glob(["*.java"]),
    javacopts = JAVAC_OPTS,
    tags = ["maven_coordinates=com.google.inject.extensions:guice-throwingproviders:" + POM_VERSION],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr305_annotations",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-throwingproviders",
    artifact_name = "Google Guice - Extensions - ThrowingProviders",
    artifact_target = ":throwingproviders",
    is_extension = True,
    javadoc_srcs = [":javadoc-srcs"],
)
