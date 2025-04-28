load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "JAVAC_OPTS")
load("//:test_defs.bzl", "guice_test_suites")

package(
    default_testonly = 1,
)

ERROR_MESSAGE_TEST_UTILS_SRCS = ["ErrorMessageTestUtils.java"]

java_library(
    name = "error_message_test_utils",
    srcs = ERROR_MESSAGE_TEST_UTILS_SRCS,
    javacopts = JAVAC_OPTS,
    deps = [
        "//third_party/java/guava/io",
        "//third_party/java/truth",
    ],
)

java_library(
    name = "tests",
    srcs = glob(
        ["*.java"],
        exclude = ERROR_MESSAGE_TEST_UTILS_SRCS,
    ),
    javacopts = ["-Xep:BetaApi:OFF"] + JAVAC_OPTS,
    resources = [
        ":test_error_files",
    ],
    deps = [
        ":error_message_test_utils",
        "//core/src/com/google/inject",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jakarta_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

filegroup(
    name = "test_error_files",
    srcs = glob(["testdata/*.txt"]),
)

[guice_test_suites(
    name = "gen_tests_stack_trace%s_use_method_handles_%s" % (include_stack_trace_option, use_method_handles_option),
    args = [
        "--guice_include_stack_traces=%s" % include_stack_trace_option,
        "--guice_use_method_handles=%s" % use_method_handles_option,
    ],
    sizes = [
        "small",
    ],
    suffix = "_stack_trace_%s_use_method_handles_%s" % (include_stack_trace_option, use_method_handles_option),
    deps = [":tests"],
) for include_stack_trace_option in [
    "OFF",
    "ONLY_FOR_DECLARING_SOURCE",
] for use_method_handles_option in [
    "NO",
    "YES",
]]
