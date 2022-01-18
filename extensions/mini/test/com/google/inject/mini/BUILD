# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//core/src/com/google/inject",
        "//extensions/mini/src/com/google/inject/mini",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [":tests"],
)
