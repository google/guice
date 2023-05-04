# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)

load("@rules_java//java:defs.bzl", "java_library")
load("//:mvn.bzl", "gen_maven_artifact")
load(
    "//:build_defs.bzl",
    "JAVAC_OPTS",
    "POM_VERSION",
)

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
        exclude = IMPLEMENTED_BY_SRCS + PROVIDED_BY_SRCS + ANNOTATION_SRCS + CALLER_FINDER_COMMON_SRCS,
    ),
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
    tags = ["maven_coordinates=com.google.inject:guice:" + POM_VERSION],
    exports = [
        ":annotations",
        ":implemented_by",
        ":provided_by",
        # TODO(user): caller_finder_common shouldn't be exported, but validation requires
        # it be exported right now.
        ":caller_finder_common",
    ],
    deps = [
        # Warning: Be very careful adding new dependencies,
        # These all have to mirrored in the open-source
        # build (and jarjar'd up).
        ":annotations",
        ":caller_finder_common",
        ":implemented_by",
        ":provided_by",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/cache",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/primitives",
        "//third_party/java/aopalliance",
        "//third_party/java/asm",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jakarta_inject",
    ],
)

java_library(
    name = "annotations",
    srcs = ANNOTATION_SRCS,
    javacopts = JAVAC_OPTS,
    plugins = [
    ],
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
        ":provided_by",
    ],
    javadoc_srcs = [":javadoc-srcs"],
)
