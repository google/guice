# see https://coderwall.com/p/9b_lfq

if [ "$TRAVIS_REPO_SLUG" == "google/guice" ] && \
   [ "$TRAVIS_JDK_VERSION" == "oraclejdk8" ] && \
   [ "$LABEL" == "mvn" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing maven snapshot...\n"

  cd $HOME
  git clone --quiet --branch=travis https://github.com/google/guice travis > /dev/null
  cd -
  
  mvn clean deploy --settings="$HOME/travis/settings.xml" -DskipTests=true -Dmaven.javadoc.skip=true

  echo -e "Published maven snapshot"
fi
