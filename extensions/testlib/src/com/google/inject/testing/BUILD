# Copyright 2022 Google Inc.  All rights reserved.
load("@rules_java//java:defs.bzl", "java_library")
load("//:mvn.bzl", "gen_maven_artifact")
load(
    "//:build_defs.bzl",
    "POM_VERSION",
)

package(
    default_testonly = 1,
    default_visibility = ["//:src"],
)

java_library(
    name = "testlib",
    tags = ["maven_coordinates=com.google.inject.extensions:guice-testlib:" + POM_VERSION],
    exports = [
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "guice-testlib",
    artifact_name = "Google Guice - Extensions - TestLib",
    artifact_target = ":testlib",
    artifact_target_libs = [
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders",
    ],
    is_extension = True,
    javadoc_srcs = [
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder:javadoc-srcs",
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders:javadoc-srcs",
    ],
)
