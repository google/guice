# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(
    default_testonly = 1,
)

java_library(
    name = "tests",
    srcs = glob(
        ["**/*.java"],
        exclude = [
            "subpkg/SubpackageTestPrivateFallbackOnly.java",
        ],
    ),
    deps = [
        "//core/src/com/google/inject",
        "//core/test/com/google/inject:testsupport",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",
        "//third_party/java/aopalliance",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jakarta_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

java_library(
    name = "SubpackageTestPrivateFallbackOnly",
    srcs = [
        "subpkg/SubpackageTestPrivateFallbackOnly.java",
    ],
    deps = [
        "//core/src/com/google/inject",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jakarta_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [
        ":tests",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",  # bazel requires this b/c of package_info.java
    ],
)

[guice_test_suites(
    name = "gen_tests_%s" % include_stack_trace_option,
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
    ],
    sizes = [
        "small",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [
        ":tests",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",  # bazel requires this b/c of package_info.java
    ],
) for include_stack_trace_option in [
    "OFF",
]]

guice_test_suites(
    name = "gen_SubpackageTestPrivateFallbackOnly_tests",
    srcs = ["subpkg/SubpackageTestPrivateFallbackOnly.java"],
    sizes = ["small"],
    suffix = "SubpackageTestPrivateFallbackOnly",
    deps = [
        ":SubpackageTestPrivateFallbackOnly",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",  # bazel requires this b/c of package_info.java
    ],
)

[guice_test_suites(
    name = "gen_SubpackageTestPrivateFallbackOnly_tests_%s" % include_stack_trace_option,
    srcs = ["subpkg/SubpackageTestPrivateFallbackOnly.java"],
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
    ],
    sizes = [
        "small",
    ],
    suffix = "_SubpackageTestPrivateFallbackOnly_stack_trace_%s" % include_stack_trace_option,
    deps = [
        ":SubpackageTestPrivateFallbackOnly",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",  # bazel requires this b/c of package_info.java
    ],
) for include_stack_trace_option in [
    "OFF",
]]
