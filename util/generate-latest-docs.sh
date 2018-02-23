# see http://benlimmer.com/2013/12/26/automatically-publish-javadoc-to-gh-pages-with-travis-ci/ for details

if [ "$TRAVIS_REPO_SLUG" == "google/guice" ] && \
   [ "$TRAVIS_JDK_VERSION" == "oraclejdk8" ] && \
   [ "$LABEL" == "ant" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing javadoc & JDiff...\n"
  rm -rf build/docs
  ant javadoc jdiff
  cp -R build/docs/javadoc $HOME/javadoc-latest
  cp -R build/docs/latest-api-diffs $HOME/api-diffs-latest
  cp lib/build/jdiff/*.gif $HOME/api-diffs-latest/

  cd $HOME
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/google/guice gh-pages > /dev/null
  
  cd gh-pages
  git rm -rf api-docs/latest/api-diffs api-docs/latest/javadoc
  mkdir -p api-docs/latest
  cp -rf $HOME/api-diffs-latest api-docs/latest/api-diffs
  cp -rf $HOME/javadoc-latest api-docs/latest/javadoc
  git add -f .
  git commit -m "Latest javadoc & api-diffs on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to gh-pages"
  git push -fq origin gh-pages > /dev/null

  echo -e "Published Javadoc & JDiff to gh-pages.\n"
fi
