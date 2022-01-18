# Copyright 2014 Google Inc.  All rights reserved.
# Author: eatnumber1@google.com (Russ Harmon)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["*.java"]),
    plugins = [
    ],
    deps = [
        "//core/src/com/google/inject",
        "//core/test/com/google/inject:testsupport",
        "//extensions/testlib/src/com/google/inject/testing/fieldbinder",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [
        ":tests",
    ],
)

guice_test_suites(
    name = "gen_tests_OFF",
    args = [
        "--guice_include_stack_traces=OFF",
    ],
    sizes = ["small"],
    suffix = "_stack_trace_OFF",
    deps = [
        ":tests",
    ],
)
