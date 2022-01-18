# Copyright 2011 Google Inc. All Rights Reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//core/src/com/google/inject",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//extensions/struts2/src/com/google/inject/struts2",
        "//third_party/java/junit",
        "//third_party/java/struts2",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [":tests"],
)

[guice_test_suites(
    name = "gen_tests_%s" % include_stack_trace_option,
    args = ["--guice_include_stack_traces=%s" % include_stack_trace_option],
    sizes = [
        "small",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [":tests"],
) for include_stack_trace_option in [
    "OFF",
]]
