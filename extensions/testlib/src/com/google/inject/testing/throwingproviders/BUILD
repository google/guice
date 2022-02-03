load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
)

package(
    default_testonly = 1,
    default_visibility = ["//:src"],
)

java_library(
    name = "throwingproviders",
    srcs = glob(["*.java"]),
    javacopts = JAVAC_OPTS,
    deps = [
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/truth",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)
