#!/bin/bash

bash -e util/generate-latest-docs.sh
echo -e "Publishing javadoc & JDiff...\n"
mkdir -p $HOME/guice-docs/latest
cp -R build/docs/* $HOME/guice-docs/latest/

cd $HOME
git config --global user.email "guice-dev+github@google.com"
git config --global user.name "guice-dev+github"
git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/google/guice gh-pages > /dev/null

cd gh-pages
git rm -rf api-docs/latest
mkdir -p api-docs/latest
cp -rf $HOME/guice-docs/latest/* api-docs/latest/
git add -f .
git commit -m "Latest javadoc & api-diffs on successful CI build $GITHUB_SHA auto-pushed to gh-pages"
git push -fq origin gh-pages > /dev/null

echo -e "Published Javadoc & JDiff to gh-pages.\n"
