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

load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
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
    expected = [lib.label for lib in target[ExportInfo].exports.to_list() if str(lib.label) not in ctx.attr.optional_target_libs]
    actual = [lib.label for lib in ctx.attr.actual_target_libs if lib.label not in ctx.attr.optional_target_libs]
    missing = sorted(['"{}"'.format(x) for x in expected if x not in actual])
    extra = sorted(['"{}"'.format(x) for x in actual if x not in expected])
    if missing or extra:
        expected_formatted = "\n\t\t".join(sorted(['"{}"'.format(x) for x in expected]))
        actual_formatted = "\n\t\t".join(sorted(['"{}"'.format(x) for x in actual]))
        fail("\t[Error]: missing or extra target in artifact_target_libs: " +
             "\n\t expected = [" + expected_formatted + "]" +
             "\n\t actual = [" + actual_formatted + "]")

# This rule exists to perform an assertion during the Starlark analysis phase, causing any macro or
# BUILD file instantiating it to fail to generate.
_validate_target_libs_binary = rule(
    implementation = _validate_target_libs_rule_impl,
    attrs = {
        "target": attr.label(aspects = [_collect_exports_aspect]),
        "actual_target_libs": attr.label_list(),

        # This is a string_list instead of label_list to allow exluding
        # transitive exports for which the caller lacks visibility.
        "optional_target_libs": attr.string_list(),
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
        optional_artifact_target_libs = [],
        is_extension = False):
    """Generates files required for a maven artifact.

    Args:
        name: The name associated with various output
        artifact_name: The name of the generated artifcat in maven, e.g. "Google Guice Core Library".
        artifact_id: The id of the generated artifact in maven, e.g. "guice".
        artifact_target: The target containing the actual maven target.
        artifact_target_libs: The list of dependencies that should be packaged together with artifact_target,
            corresponding to the list of targets exported by artifact_target.
        optional_artifact_target_libs: The list of labels that are allowed to be
            omitted from artifact_target_libs. Other than these exceptions, all
            transitive exports of artifact_target must be included in
            artifact_target_libs.
        javadoc_srcs: Source files used to generate the Javadoc maven artifact.
        packaging: The packaging used for the artifact, default is "jar".
        is_extension: Whether the maven artifact is a Guice extension or not.
    """

    _validate_target_libs_binary(
        name = name + "_validate_target_libs",
        target = artifact_target,
        actual_target_libs = artifact_target_libs,
        optional_target_libs = optional_artifact_target_libs,
    )

    group_id = "com.google.inject"
    if is_extension:
        group_id = "com.google.inject.extensions"

    # Ideally we would get artifact_target_libs from bazel and remove the need
    # to pass this in explictly. However, this doesn't seem possible in Starlark.
    # A more significant refactoring of how we instantiate the rules here, or
    # extensions to the rule implementation(s), may be necessary.
    artifact_targets = [artifact_target] + artifact_target_libs

    pom_file(
        name = "pom",
        # pom_file already scans the transitive deps and exports
        # so passing artifact_targets instead of artifact_target seems to be
        # redundant.
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
            # javadoc_library already collects the transitive deps
            # so passing artifact_targets instead of artifact_target appears to
            # be redundant
            deps = artifact_targets,
        )

def _src_jar(target):
    if target.startswith(":"):
        target = Label("//" + native.package_name() + target)
    else:
        target = Label(target)
    return "//%s:lib%s-src.jar" % (target.package, target.name)
