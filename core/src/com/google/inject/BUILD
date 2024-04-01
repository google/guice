load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
    "POM_VERSION",
)
load("//:mvn.bzl", "gen_maven_artifact")

# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)

package(
    default_visibility = ["//:src"],
)

CALLER_FINDER_COMMON_SRCS = [
    "internal/util/CallerFinder.java",
    "internal/util/NewThrowableFinder.java",
]

ANNOTATION_SRCS = [
    "BindingAnnotation.java",
    "Exposed.java",
    "Inject.java",
    "Provides.java",
    "ScopeAnnotation.java",
    "Singleton.java",
]

PROVIDED_BY_SRCS = [
    "ProvidedBy.java",
]

IMPLEMENTED_BY_SRCS = [
    "ImplementedBy.java",
]

CALLER_FINDER_STACK_WALKER_SRCS = ["internal/util/DirectStackWalkerFinder.java"]

java_library(
    name = "caller_finder_impl",
    srcs = CALLER_FINDER_STACK_WALKER_SRCS,
    javacopts = JAVAC_OPTS,
    deps = [":caller_finder_common"],
)

java_library(
    name = "caller_finder_common",
    srcs = CALLER_FINDER_COMMON_SRCS,
    javacopts = JAVAC_OPTS,
)

java_library(
    name = "implemented_by",
    srcs = IMPLEMENTED_BY_SRCS,
    javacopts = JAVAC_OPTS,
)

java_library(
    name = "provided_by",
    srcs = PROVIDED_BY_SRCS,
    javacopts = JAVAC_OPTS,
    deps = [
        "//third_party/java/jakarta_inject",
    ],
)

# TODO(lukes,user): It'd be nice if this wasn't one big rule.
# Unfortunately, splitting it apart would not be easy. We looked into
# it and the main issues appear to be:
#   - Utility classes like internal/MoreTypes (choke point dependencies)
#   - Cyclical dependencies between Binder and spi/Element
java_library(
    name = "inject",
    srcs = glob(
        ["**/*.java"],
        exclude = IMPLEMENTED_BY_SRCS +
                  PROVIDED_BY_SRCS +
                  ANNOTATION_SRCS +
                  CALLER_FINDER_COMMON_SRCS +
                  CALLER_FINDER_STACK_WALKER_SRCS,
    ),
    javacopts = JAVAC_OPTS,
    tags = ["maven_coordinates=com.google.inject:guice:" + POM_VERSION],
    exports = [
        ":annotations",
        ":caller_finder_common",
        ":caller_finder_impl",
        ":implemented_by",
        ":provided_by",
    ],
    deps = [
        ":annotations",
        ":caller_finder_common",
        ":caller_finder_impl",
        ":implemented_by",
        ":provided_by",
        "//third_party/java/aopalliance",
        "//third_party/java/asm",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/cache",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/primitives",
        "//third_party/java/jakarta_inject",
        "//third_party/java/jsr305_annotations",
    ],
)

java_library(
    name = "annotations",
    srcs = ANNOTATION_SRCS,
    javacopts = JAVAC_OPTS,
    deps = [
        "//third_party/java/error_prone:annotations",
        "//third_party/java/jakarta_inject",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(
        ["**/*.java"],
        exclude = ["internal/**/*.java"],
    ),
)

gen_maven_artifact(
    name = "core",
    artifact_id = "guice",
    artifact_name = "Google Guice - Core Library",
    artifact_target = ":inject",
    artifact_target_libs = [
        ":annotations",
        ":implemented_by",
        ":caller_finder_common",
        ":caller_finder_impl",
        ":provided_by",
    ],
    javadoc_srcs = [":javadoc-srcs"],
)
