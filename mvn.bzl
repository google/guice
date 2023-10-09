# Copyright (C) 2022 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""starlark macros to generate maven files."""

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")
load("@google_bazel_common//tools/maven:pom_file.bzl", "pom_file")

ExportInfo = provider(
    "Export information",
    fields = {
        "exports": "A depset containing the transitive exports from the target",
    },
)

# @unused target is unused but required because it's part of the aspect API
def _collect_exports_aspect_impl(target, ctx):
    exports = getattr(ctx.rule.attr, "exports", [])
    transitive = [target[ExportInfo].exports for target in exports if ExportInfo in target]
    return [ExportInfo(exports = depset(exports, transitive = transitive))]

_collect_exports_aspect = aspect(
    implementation = _collect_exports_aspect_impl,
    attr_aspects = ["exports"],
)

def _validate_target_libs_rule_impl(ctx):
    """Validates the transitive exports of the maven artifacts.

    If the main maven artifact target exports other targets, those exported targets need to be
    included in the artifact_target_libs, so that they get packaged into the final deployable
    artifact.
    """
    target = ctx.attr.target
    expected = [lib.label for lib in target[ExportInfo].exports.to_list()]
    actual = [lib.label for lib in ctx.attr.actual_target_libs]
    missing = sorted(['"{}"'.format(x) for x in expected if x not in actual])
    extra = sorted(['"{}"'.format(x) for x in actual if x not in expected])
    if missing or extra:
        expected_formatted = "\n\t\t".join(sorted(['"{}"'.format(x) for x in expected]))
        actual_formatted = "\n\t\t".join(sorted(['"{}"'.format(x) for x in actual]))
        fail("\t[Error]: missing or extra target in artifact_target_libs: " +
             "\n\t expected = [" + expected_formatted + "]" +
             "\n\t actual = [" + actual_formatted + "]")

_validate_target_libs_rule = rule(
    implementation = _validate_target_libs_rule_impl,
    attrs = {
        "target": attr.label(aspects = [_collect_exports_aspect]),
        "actual_target_libs": attr.label_list(),
    },
)

def gen_maven_artifact(
        name,
        artifact_name,
        artifact_id,
        artifact_target,
        javadoc_srcs,
        packaging = "jar",
        artifact_target_libs = [],
        is_extension = False):
    """Generates files required for a maven artifact.

    Args:
        name: The name associated with various output
        artifact_name: The name of the generated artifcat in maven, e.g. "Google Guice Core Library".
        artifact_id: The id of the generated artifact in maven, e.g. "guice".
        artifact_target: The target containing the actual maven target.
        artifact_target_libs: The list of dependencies that should be packaged together with artifact_target,
            corresponding to the list of targets exported by artifact_target.
        javadoc_srcs: Source files used to generate the Javadoc maven artifact.
        packaging: The packaging used for the artifact, default is "jar".
        is_extension: Whether the maven artifact is a Guice extension or not.

    """

    _validate_target_libs_rule(
        name = name + "_validate_target_libs",
        target = artifact_target,
        actual_target_libs = artifact_target_libs,
    )

    group_id = "com.google.inject"
    if is_extension:
        group_id = "com.google.inject.extensions"

    # TODO: get artifact_target_libs from bazel and remove the need to pass this in explictly.
    artifact_targets = [artifact_target] + artifact_target_libs

    pom_file(
        name = "pom",
        targets = artifact_targets,
        preferred_group_ids = [
            "com.google.inject",
            "com.google",
        ],
        template_file = "//:pom-template.xml",
        substitutions = {
            "{artifact_name}": artifact_name,
            "{artifact_id}": artifact_id,
            "{artifact_group_id}": group_id,
            "{packaging}": packaging,
        },
    )

    if packaging == "jar":
        jarjar_library(
            name = artifact_id,
            jars = artifact_targets,
        )

        jarjar_library(
            name = artifact_id + "-src",
            jars = [_src_jar(dep) for dep in artifact_targets],
        )

    if javadoc_srcs:
        javadoc_library(
            name = artifact_id + "-javadoc",
            srcs = javadoc_srcs,
            testonly = 1,
            deps = artifact_targets,
        )

def _src_jar(target):
    if target.startswith(":"):
        target = Label("//" + native.package_name() + target)
    else:
        target = Label(target)
    return "//%s:lib%s-src.jar" % (target.package, target.name)
