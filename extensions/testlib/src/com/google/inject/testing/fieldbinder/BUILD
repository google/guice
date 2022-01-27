# Copyright 2014 Google Inc.  All rights reserved.
# Author: eatnumber1@google.com (Russ Harmon)
load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
)

package(
    default_testonly = 1,
    default_visibility = ["//:src"],
)

java_library(
    name = "fieldbinder",
    srcs = glob(["*.java"]),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
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
