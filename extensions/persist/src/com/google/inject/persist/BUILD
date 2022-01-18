# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
package(
    default_visibility = ["//:src"],
)

java_library(
    name = "persist",
    srcs = glob(["**/*.java"]),
    plugins = [
    ],
    deps = [
        "//core/src/com/google/inject",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//third_party/java/aopalliance",
        "//third_party/java/guava/annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javax_persistence",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)
