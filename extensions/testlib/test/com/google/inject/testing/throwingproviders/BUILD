load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "guice_test_suites")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//extensions/testlib/src/com/google/inject/testing/throwingproviders",
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

guice_test_suites(
    name = "gen_tests",
    sizes = ["small"],
    deps = [":tests"],
)
