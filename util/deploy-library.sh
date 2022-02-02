#!/bin/bash

set -eu

# Builds and deploys the given artifacts to a configured maven goal.
# @param {string} library the library to deploy.
# @param {string} pomfile the pom file to deploy.
# @param {string} srcjar the sources jar of the library. This is an optional
# parameter, if provided then javadoc must also be provided.
# @param {string} javadoc the java doc jar of the library. This is an optional
# parameter, if provided then srcjar must also be provided.
deploy_library() {
  local library=$1
  local pomfile=$2
  local srcjar=$3
  local javadoc=$4
  local mvn_goal=$5
  local version_name=$6
  shift 6
  local extra_maven_args=("$@")

  bazel build --define=pom_version="$version_name" \
    $library $pomfile

  if [ -n "$srcjar" ] && [ -n "$javadoc" ] ; then
    bazel build --define=pom_version="$version_name" \
      $srcjar $javadoc
    mvn $mvn_goal \
      -Dfile=$(bazel_output_file $library) \
      -Djavadoc=$(bazel_output_file $javadoc) \
      -DpomFile=$(bazel_output_file $pomfile) \
      -Dsources=$(bazel_output_file $srcjar) \
      "${extra_maven_args[@]:+${extra_maven_args[@]}}"
  else
    mvn $mvn_goal \
      -Dfile=$(bazel_output_file $library) \
      -DpomFile=$(bazel_output_file $pomfile) \
      "${extra_maven_args[@]:+${extra_maven_args[@]}}"
  fi
}

bazel_output_file() {
  local library=$1
  local output_file=bazel-bin/$library
  if [[ ! -e $output_file ]]; then
     output_file=bazel-genfiles/$library
  fi
  if [[ ! -e $output_file ]]; then
    echo "Could not find bazel output file for $library"
    exit 1
  fi
  echo -n $output_file
}

deploy_library "$@"