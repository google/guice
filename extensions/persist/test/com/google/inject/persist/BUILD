# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

# This has an additional dependency on a genrule config:metainf,
# because we need that file to end up in META-INF/persistence.xml,
# but due to the way the packages are built, it's hard to do that.
# It's even more confusing because in the open-source repository,
# that file *is* in the proper place.

java_import(
    name = "metainf",
    jars = ["//extensions/persist/test/com/google/inject/persist/config:metainf"],
)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        ":metainf",
        "//core/src/com/google/inject",
        "//extensions/persist/src/com/google/inject/persist",
        "//third_party/java/hamcrest",
        "//third_party/java/hibernate:hibernate5",
        "//third_party/java/hsqldb:hsqldb2",
        "//third_party/java/javax_persistence",
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
