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
    name = "jndi",
    srcs = glob(["*.java"]),
    javacopts = JAVAC_OPTS,
    tags = ["maven_coordinates=com.google.inject.extensions:guice-jndi:" + POM_VERSION],
    deps = [
        "//core/src/com/google/inject",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-jndi",
    artifact_name = "Google Guice - Extensions - JNDI",
    artifact_target = ":jndi",
    is_extension = True,
    javadoc_srcs = [
        ":javadoc-srcs",
    ],
)
