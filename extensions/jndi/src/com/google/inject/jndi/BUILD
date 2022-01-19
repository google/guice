# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//:src"],
)

java_library(
    name = "jndi",
    srcs = glob(["*.java"]),
    deps = [
        "//core/src/com/google/inject",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)
