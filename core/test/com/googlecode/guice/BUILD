# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)

load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(
    default_testonly = 1,
    default_visibility = ["//:src"],
)

java_library(
    name = "tests",
    srcs = glob(
        ["**/*.java"],
        exclude = ["*Tck.java"],
    ),
    deps = [
        "//core/src/com/google/inject",
        "//core/test/com/google/inject:testsupport",
        "//third_party/java/aopalliance",
        "//third_party/java/guava/testing",
        "//third_party/java/jakarta_inject",
        "//third_party/java/junit",
        "@maven//:biz_aQute_bnd",
        "@maven//:org_apache_felix_org_apache_felix_framework",
    ],
)

java_library(
    name = "jakarta_tck_test",
    srcs = ["GuiceJakartaTck.java"],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/jakarta_inject",
        "//third_party/java/jakarta_inject_tck",
        "//third_party/java/junit",
    ],
)

guice_test_suites(
    name = "gen_tests",
    srcs = glob(
        ["**/*Test.java"],
        exclude = ["*Tck.java"],
    ),
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
    srcs = glob(
        ["**/*Test.java"],
        exclude = ["*Tck.java"],
    ),
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

guice_test_suites(
    name = "gen_jakarta_tck_tests",
    srcs = ["GuiceJakartaTck.java"],
    sizes = ["small"],
    suffix = "_jakarta_tck",
    deps = [":jakarta_tck_test"],
)
