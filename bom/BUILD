load("@rules_java//java:defs.bzl", "java_library")
load("//:mvn.bzl", "gen_maven_artifact")
load("//:build_defs.bzl", "POM_VERSION")

package(
    default_testonly = 1,
)

java_library(
    name = "guice-bom",
    tags = ["maven_coordinates=com.google.inject:guice-bom:" + POM_VERSION],
    runtime_deps = [
        "//core/src/com/google/inject",
        "//extensions/assistedinject/src/com/google/inject/assistedinject",
        "//extensions/dagger-adapter/src/com/google/inject/daggeradapter",
        "//extensions/grapher/src/com/google/inject/grapher",
        "//extensions/jmx/src/com/google/inject/tools/jmx",
        "//extensions/jndi/src/com/google/inject/jndi",
        "//extensions/persist/src/com/google/inject/persist",
        "//extensions/servlet/src/com/google/inject/servlet",
        "//extensions/spring/src/com/google/inject/spring",
        "//extensions/struts2/src/com/google/inject/struts2",
        "//extensions/testlib/src/com/google/inject/testing:testlib",
        "//extensions/throwingproviders/src/com/google/inject/throwingproviders",
    ],
)

gen_maven_artifact(
    name = "bom",
    artifact_id = "guice-bom",
    artifact_name = "Google Guice - Bill of Materials",
    artifact_target = ":guice-bom",
    javadoc_srcs = None,
    packaging = "pom",
)
