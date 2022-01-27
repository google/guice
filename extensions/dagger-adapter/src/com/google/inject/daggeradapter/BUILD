# Copyright 2015 Google Inc.  All rights reserved.
# Author: cgruber@google.com (Christian Gruber)
load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
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
