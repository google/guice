# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    plugins = [
    ],
    deps = [
        "//core/src/com/google/inject",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",
        "//extensions/grapher/src/com/google/inject/grapher",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
        "medium",
    ],
    deps = [":tests"],
)

[guice_test_suites(
    name = "gen_tests_%s" % include_stack_trace_option,
    args = ["--guice_include_stack_traces=%s" % include_stack_trace_option],
    sizes = [
        "small",
        "medium",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [":tests"],
) for include_stack_trace_option in [
    "OFF",
]]
