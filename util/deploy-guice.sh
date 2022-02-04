#!/bin/bash

# Example usage: deploy-guice.sh install LOCAL-SNAPSHOT

set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")

# Builds and deploys the given artifacts to a configured maven goal.
# @param {string} library the library to deploy.
# @param {string} pomfile the pom file to deploy.
# @param {string} srcjar the sources jar of the library. This is an optional
# parameter, if provided then javadoc must also be provided.
# @param {string} javadoc the java doc jar of the library. This is an optional
# parameter, if provided then srcjar must also be provided.
_deploy() {
  local library=$1
  local pomfile=$2
  local srcjar=$3
  local javadoc=$4
  bash $(dirname $0)/deploy-library.sh \
      "$library" \
      "$pomfile" \
      "$srcjar" \
      "$javadoc" \
      "$MVN_GOAL" \
      "$VERSION_NAME" \
      "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

_deploy_extension() {
    local ext=$1
    dir="$ext"
    artifact_id="guice-$ext"
    if [[ $ext == 'dagger-adapter' ]]; then
        dir="daggeradapter"
    elif [[ $ext == 'testlib' ]]; then
        dir="testing"
    elif [[ $ext == 'jmx' ]]; then
        dir="tools/jmx"
    fi

    local jar_file="extensions/$ext/src/com/google/inject/$dir/${artifact_id}.jar"
    local pom_file="extensions/$ext/src/com/google/inject/$dir/pom.xml"
    local srcjar="extensions/$ext/src/com/google/inject/$dir/${artifact_id}-src.jar"
    local javadoc="extensions/$ext/src/com/google/inject/$dir/${artifact_id}-javadoc.jar"

    echo "Deploying $pom_file"
    _deploy \
        "$jar_file" \
        "$pom_file" \
        "$srcjar" \
        "$javadoc"
}

_deploy \
  bom/libguice-bom.jar \
  bom/pom.xml \
  "" \
  ""

_deploy \
  core/src/com/google/inject/guice.jar \
  core/src/com/google/inject/pom.xml \
  core/src/com/google/inject/guice-src.jar \
  core/src/com/google/inject/guice-javadoc.jar

EXTENSIONS=( $(ls -1 $(dirname $0)/../extensions) )
for ext in "${EXTENSIONS[@]}"
do
  if [[ -d "extensions/$ext" ]]; then
    _deploy_extension $ext
  fi
done