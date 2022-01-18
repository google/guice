# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    deps = [
        "//extensions/service/src/com/google/inject/service",
        "//third_party/java/junit",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["medium"],
    deps = [":tests"],
)
