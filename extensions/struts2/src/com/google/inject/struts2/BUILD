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
    name = "struts2",
    srcs = glob(["*.java"]),
    javacopts = JAVAC_OPTS,
    deps = [
        "//core/src/com/google/inject",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//third_party/java/struts2",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)
