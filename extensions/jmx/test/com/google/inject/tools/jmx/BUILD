# Copyright 2011 Google Inc.  All rights reserved.
# Author: sameb@google.com (Sam Berlin)
load("@rules_java//java:defs.bzl", "java_library")

package(default_testonly = 1)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//core/src/com/google/inject",
        "//extensions/jmx/src/com/google/inject/tools/jmx",
    ],
)

# There are no actual tests here, just some code that's intended to be
# run manually.
# TODO(sameb): Add some tests.
#load("//testing/build_defs:junit3_test_suites.bzl", "junit3_test_suites")
#junit3_test_suites(name = "gen_tests",
#                 deps = [ ":tests", ],
#                 sizes = [ 'small', ],
#                 include_suites = True,
#                 subpackages = True,)
