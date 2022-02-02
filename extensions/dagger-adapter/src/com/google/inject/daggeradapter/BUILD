# Copyright 2015 Google Inc.  All rights reserved.
# Author: cgruber@google.com (Christian Gruber)
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
    name = "daggeradapter",
    srcs = glob(["*.java"]),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    tags = ["maven_coordinates=com.google.inject.extensions:guice-dagger-adapter:" + POM_VERSION],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/dagger",
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
    artifact_id = "guice-dagger-adapter",
    artifact_name = "Google Guice - Extensions - Dagger",
    artifact_target = ":daggeradapter",
    is_extension = True,
    javadoc_srcs = [":javadoc-srcs"],
)
