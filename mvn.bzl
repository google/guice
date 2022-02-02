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

def gen_maven_artifact(
        name,
        artifact_name,
        artifact_id,
        artifact_target,
        javadoc_srcs,
        is_extension = False):
    """Generates files required for a maven artifact.

    Args:
        name: The name associated with various output
        artifact_name: The name of the generated artifcat in maven, e.g. "Google Guice Core Library".
        artifact_id: The id of the generated artifact in maven, e.g. "guice".
        artifact_target: The target containing the actual maven target.
        javadoc_srcs: Source files used to generate the Javadoc maven artifact.
        is_extension: Whether the maven artifact is a Guice extension or not.

    """

    group_id = "com.google.inject"
    if is_extension:
        group_id = "com.google.inject.extensions"

    artifact_targets = [artifact_target]

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
        },
    )

    jarjar_library(
        name = artifact_id,
        jars = artifact_targets,
    )

    jarjar_library(
        name = artifact_id + "-src",
        jars = [_src_jar(dep) for dep in artifact_targets],
    )

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
