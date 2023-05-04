#!/bin/sh

while getopts o:n: flag
do
    case "${flag}" in
        o) OLD_VERSION=${OPTARG};;
        n) NEW_VERSION=${OPTARG};;
    esac
done

BASE_URL=https://repo1.maven.org/maven2/com/google/inject
REPO_DIR=$PWD

# Downloading old versions from maven central
mkdir -p /tmp/guice-jars
cd /tmp/guice-jars

echo "Old version: $OLD_VERSION, new version: $NEW_VERSION"

echo "Downloading core..."
wget $BASE_URL/guice/$OLD_VERSION/guice-$OLD_VERSION.jar

echo "Diffing core..."
pkgdiff  guice-$OLD_VERSION.jar $REPO_DIR/core/target/guice-$NEW_VERSION.jar &

# struts2 not listed because we don't build it past the 7.0 line.
for EXT in assistedinject dagger-adapter grapher jmx jndi persist servlet spring testlib throwingproviders
do
    echo "Dowloading $EXT extension..."
    wget $BASE_URL/extensions/guice-$EXT/$OLD_VERSION/guice-$EXT-$OLD_VERSION.jar
    echo "Diffing $EXT..."
    pkgdiff  guice-$EXT-$OLD_VERSION.jar $REPO_DIR/extensions/$EXT/target/guice-$EXT-$NEW_VERSION.jar &
done

python3 -m http.server --directory pkgdiff_reports/
