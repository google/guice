# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
)

package(
    default_visibility = ["//:src"],
)

java_library(
    name = "grapher",
    srcs = glob(["**/*.java"]),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["**/*.java"]),
)
