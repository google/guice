#!/bin/bash

set -eu

echo -e "Generating latest javadoc & JDiff...\n"

if [[ -d "build/docs" ]]; then
  rm -r build/docs
fi

mkdir -p build/docs/{javadoc,api-diffs}

mvn clean install -Dguice.skipTests=true
mvn javadoc:aggregate -Dguice.skipTests=true
cp -r target/site/apidocs/* build/docs/javadoc

cp util/api-diffs.index.html build/docs/api-diffs/index.html

mvn spf4j-jdiff:jdiff -pl core -Dguice.skipTests=true
cp -r core/target/site/api-diffs/* build/docs/api-diffs/

EXTENSIONS=( $(ls -1 $(dirname $0)/../extensions) )
for ext in "${EXTENSIONS[@]}"
do
    if [[ -f extensions/$ext/pom.xml ]]; then
        echo -e "Generating latest API diff for extension ${ext}\n"
        mvn spf4j-jdiff:jdiff -pl extensions/$ext -Dguice.skipTests=true
        cp -r extensions/$ext/target/site/api-diffs/* build/docs/api-diffs/
    fi
done

# Hacky way to remove the ugly blue background on jdiff reports
find build/docs/api-diffs -name stylesheet-jdiff.css \
| xargs sed -i 's/background: #CCFFFF url(background.gif);//g'
