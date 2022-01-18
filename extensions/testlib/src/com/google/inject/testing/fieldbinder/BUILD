# Copyright 2014 Google Inc.  All rights reserved.
# Author: eatnumber1@google.com (Russ Harmon)
package(
    default_testonly = 1,
    default_visibility = ["//:src"],
)

java_library(
    name = "fieldbinder",
    srcs = glob(["**/*.java"]),
    plugins = [
    ],
    deps = [
        "//core/src/com/google/inject",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
    ],
)
