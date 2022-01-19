# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//:src"],
)

ANNOTATION_SRCS = [
    "BindingAnnotation.java",
    "Exposed.java",
    "Inject.java",
    "ProvidedBy.java",
    "Provides.java",
    "ScopeAnnotation.java",
    "Singleton.java",
]

IMPLEMENTED_BY_SRCS = [
    "ImplementedBy.java",
]

java_library(
    name = "implemented_by",
    srcs = IMPLEMENTED_BY_SRCS,
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
        exclude = IMPLEMENTED_BY_SRCS + ANNOTATION_SRCS,
    ),
    plugins = [
    ],
    exports = [
        ":annotations",
        ":implemented_by",
    ],
    deps = [
        # Warning: Be very careful adding new dependencies,
        # These all have to mirrored in the open-source
        # build (and jarjar'd up).
        ":annotations",
        ":implemented_by",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/cache",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/primitives",
        "//third_party/java/aopalliance",
        "//third_party/java/asm",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "annotations",
    srcs = ANNOTATION_SRCS,
    plugins = [
    ],
    deps = [
        "//third_party/java/error_prone:annotations",
        "//third_party/java/jsr330_inject",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["**/*.java"]),
)
