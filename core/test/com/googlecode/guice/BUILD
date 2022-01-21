# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)

load("//:test_defs.bzl", "guice_test_suites")

package(
    default_testonly = 1,
    default_visibility = ["//:src"],
)

java_library(
    name = "tests",
    srcs = glob([
        "**/*.java",
    ]),
    deps = [
        "//core/src/com/google/inject",
        "//core/test/com/google/inject:testsupport",
        "//third_party/java/aopalliance",
        "//third_party/java/guava/testing",
        "//third_party/java/jsr330_inject",
        "//third_party/java/jsr330_inject:tck",
        "//third_party/java/junit",
        "@maven//:biz_aQute_bnd",
        "@maven//:org_apache_felix_org_apache_felix_framework",
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
