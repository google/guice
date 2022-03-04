# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)

load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

TEST_SUPPORT_SRCS = [
    "Asserts.java",
    "internal/WeakKeySetUtils.java",
]

# Files that are shared by extensions also.
# Typically this would go in a java/../testing package,
# but we need to work with the open-source code structure,
# so it is here.
java_library(
    name = "testsupport",
    srcs = TEST_SUPPORT_SRCS,
    javacopts = ["-Xep:BetaApi:OFF"],
    visibility = [
        "//:src",
    ],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/testing",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

# All the actual XTest classes & friends.
java_library(
    name = "tests",
    srcs = glob(
        ["**/*.java"],  # glob ignores subfolder that has its own BUILD files
        exclude = TEST_SUPPORT_SRCS + [
            "AllTests.java",
        ],
    ),
    javacopts = ["-Xep:BetaApi:OFF"],
    plugins = [
    ],
    deps = [
        ":testsupport",
        "//core/src/com/google/inject",
        "//third_party/java/aopalliance",
        "//third_party/java/asm",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/testing",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

# This target is unused, but exists so that we can more easily
# ensure the opensource build maintains compiling code.
java_library(
    name = "AllTests",
    srcs = ["AllTests.java"],
    deps = [
        ":tests",
        "//core/test/com/googlecode/guice:tests",
        "//third_party/java/junit",
    ],
)

guice_test_suites(
    name = "gen_tests",
    jvm_flags = [
        # those 2 options are required for some tests that checks stack traces
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+ShowHiddenFrames",
    ],
    sizes = [
        "small",
        "medium",
    ],
    deps = [":tests"],
)

[guice_test_suites(
    name = "gen_tests_stack_trace_%s" % include_stack_trace_option,
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
    ],
    jvm_flags = [
        # those 2 options are required for some tests that checks stack traces
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+ShowHiddenFrames",
    ],
    sizes = [
        "small",
        "medium",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [":tests"],
) for include_stack_trace_option in [
    "OFF",
]]

[guice_test_suites(
    name = "gen_tests_class_loading_%s" % custom_class_loading_option,
    args = [
        "--guice_custom_class_loading=%s" % custom_class_loading_option,
    ],
    jvm_flags = [
        # those 2 options are required for some tests that checks stack traces
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+ShowHiddenFrames",
    ],
    sizes = [
        "small",
        "medium",
    ],
    suffix = "_custom_class_loading_%s" % custom_class_loading_option,
    deps = [":tests"],
) for custom_class_loading_option in [
    "OFF",
    "CHILD",
    "ANONYMOUS",
]]
