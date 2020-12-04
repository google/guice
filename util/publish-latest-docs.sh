if [ "$TRAVIS_REPO_SLUG" == "google/guice" ] && \
   [ "$TRAVIS_JDK_VERSION" == "openjdk11" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  bash -e util/generate-latest-docs.sh
  echo -e "Publishing javadoc & JDiff...\n"
  mkdir -p $HOME/guice-docs/latest
  cp -R build/docs/* $HOME/guice-docs/latest/

  cd $HOME
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/google/guice gh-pages > /dev/null

  cd gh-pages
  git rm -rf api-docs/latest
  mkdir -p api-docs/latest
  cp -rf $HOME/guice-docs/latest/* api-docs/latest/
  git add -f .
  git commit -m "Latest javadoc & api-diffs on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to gh-pages"
  git push -fq origin gh-pages > /dev/null

  echo -e "Published Javadoc & JDiff to gh-pages.\n"
fi
