load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(
    default_testonly = 1,
)

java_library(
    name = "tests",
    srcs = glob(["*.java"]),
    javacopts = ["-Xep:BetaApi:OFF"],
    plugins = [
    ],
    resources = [
        ":test_error_files",
    ],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/io",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

filegroup(
    name = "test_error_files",
    srcs = glob(["testdata/*.txt"]),
)

[guice_test_suites(
    name = "gen_tests_stack_trace%s" % include_stack_trace_option,
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
    ],
    sizes = [
        "small",
    ],
    suffix = "_stack_trace_%s" % include_stack_trace_option,
    deps = [":tests"],
) for include_stack_trace_option in [
    "OFF",
    "ONLY_FOR_DECLARING_SOURCE",
]]
