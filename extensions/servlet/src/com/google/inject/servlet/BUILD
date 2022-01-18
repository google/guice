# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
package(
    default_visibility = ["//:src"],
)

java_library(
    name = "request-scoped-annotation",
    srcs = ["RequestScoped.java"],
    plugins = [
    ],
    deps = [
        "//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "servlet",
    srcs = glob(
        ["**/*.java"],
        exclude = ["RequestScoped.java"],
    ),
    plugins = [
    ],
    exports = [":request-scoped-annotation"],
    deps = [
        ":request-scoped-annotation",
        "//core/src/com/google/inject",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/escape",
        "//third_party/java/guava/net",
        "//third_party/java/jsr330_inject",
        "//third_party/java/servlet/servlet_api",
    ],
)
