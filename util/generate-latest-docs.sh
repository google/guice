if [ "$TRAVIS_REPO_SLUG" == "google/guice" ] && \
   [ "$TRAVIS_JDK_VERSION" == "openjdk11" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then

  echo -e "Generating latest javadoc & JDiff...\n"

  rm -r build/docs
  mkdir -p build/docs/{javadoc,api-diffs}

  mvn clean install
  mvn javadoc:aggregate
  cp -r target/site/apidocs/* build/docs/javadoc

  cp util/api-diffs.index.html build/docs/api-diffs/index.html

  mvn spf4j-jdiff:jdiff -pl core
  cp -r core/target/site/api-diffs/* build/docs/api-diffs/

  for EXT in assistedinject dagger-adapter grapher jmx jndi persist servlet spring struts2 testlib throwingproviders
  do
      mvn spf4j-jdiff:jdiff -pl extensions/$EXT
      cp -r extensions/$EXT/target/site/api-diffs/* build/docs/api-diffs/
  done

  # Hacky way to remove the ugly blue background on jdiff reports
  find build/docs/api-diffs -name stylesheet-jdiff.css \
  | xargs sed -i 's/background: #CCFFFF url(background.gif);//g'

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
