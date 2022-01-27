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
    name = "request-scoped-annotation",
    srcs = ["RequestScoped.java"],
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    deps = [
        "//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "servlet",
    srcs = glob(
        ["*.java"],
        exclude = ["RequestScoped.java"],
    ),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    exports = [":request-scoped-annotation"],
    deps = [
        ":request-scoped-annotation",
        "//core/src/com/google/inject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/escape",
        "//third_party/java/guava/net",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)
