# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(
    default_testonly = 1,
)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    javacopts = ["-Xep:FutureReturnValueIgnored:OFF"],
    deps = [
        "//core/src/com/google/inject",
        "//core/test/com/google/inject:testsupport",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jakarta_servlet_api",
        "//third_party/java/junit",
        "//third_party/java/mockito",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = [
        "small",
        "medium",
    ],
    deps = [
        ":tests",
        "//extensions/servlet/src/com/google/inject/servlet",  # bazel requires this b/c of package_info.java
    ],
)

[guice_test_suites(
    name = "gen_tests_%s" % include_stack_trace_option,
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
    ],
    sizes = [
        "small",
        "medium",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [
        ":tests",
        "//extensions/servlet/src/com/google/inject/servlet",  # bazel requires this b/c of package_info.java
    ],
) for include_stack_trace_option in [
    "OFF",
]]
