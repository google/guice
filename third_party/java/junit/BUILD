load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "junit",
    testonly = 1,
    exports = [
        "@maven//:junit_junit",
        "@maven//:org_junit_jupiter_junit_jupiter_api",
        "@maven//:org_junit_jupiter_junit_jupiter_engine",
        "@maven//:org_junit_platform_junit_platform_commons",
        "@maven//:org_junit_platform_junit_platform_engine",
        "@maven//:org_junit_platform_junit_platform_launcher",
        "@maven//:org_junit_vintage_junit_vintage_engine",
        "@maven//:org_opentest4j_opentest4j",
        "@maven//:org_hamcrest_hamcrest",
    ],
)
