#!/bin/bash
## Compares the ant jars to the maven jars and makes sure they're the same
## (or different where/how expected)

## Note: The no_aop build doesn't compare cleanly for some reason.
## Maybe a difference between the ant & maven munge preprocessor?

RETVAL=0

function cleanAndBuild {
  mvn clean > /dev/null
  ant clean.all > /dev/null
  #ant no_aop > /dev/null
  ant dist > /dev/null
  mvn package -DskipTests=true -Dmaven.javadoc.skip=true > /dev/null
  #ant -f build/no_aop/build.xml dist > /dev/null
}

function findAndCompareJars {
  version=4.0
  for ANT in `find ./build/dist/* -name "*-snapshot.jar" `
  do
    if [ $ANT = "./build/dist/guice-snapshot.jar" ]; then
      ## Check the main build.
      MVN=./core/target/guice-$version-SNAPSHOT.jar
      extension=core
      compareJars "$ANT" "$MVN" $extension
      #compareJars "./build/no_aop/$ANT" "./core/target/guice-$version-SNAPSHOT-no_aop.jar" "no_aop: $extension" 
    else
      ## Check extensions.
      extension=`echo $ANT | awk -F"-" '{print $2 }'` 
      MVN=./extensions/$extension/target/guice-$extension-$version-SNAPSHOT.jar  
      compareJars "$ANT" "$MVN" $extension
    fi

  done;
}

function compareJars {
  ANT=$1
  MVN=$2
  extension=$3
  curdir=`pwd`
 
  echo Comparing $3
  mkdir "tmp$$"
  cp $ANT tmp$$/ant.jar
  cp $MVN tmp$$/mvn.jar
  cd "tmp$$" 
  mkdir ant
  mkdir mvn
  cd ant
  jar -xf ../ant.jar
  cd ..
  cd mvn
  jar -xf ../mvn.jar
  cd ..
  
  ## ant puts LICENSE & NOTICE files in a different place
  echo LICENSE > excludes
  echo NOTICE >> excludes
  ## ant does not create DEPENDENCIES
  echo DEPENDENCIES >> excludes
  ## ant/mvn slightly different in MANIFEST.MF
  echo MANIFEST.MF >> excludes
  
  ## ant leaves empty directories for some jarjar'd paths --
  ## we grep -v instead of exclude because we want to make sure
  ## if any files in those directories exist, that they're diff'd.
  ## ant 1.8+ also creates package-info classes all the time, and
  ## maven doesn't -- so we just ignore the package-info classes.
  diff -u --recursive -Xexcludes ant mvn | \
     grep -v "^Only in ant/com/google/inject/internal/asm: signature$" | \
     grep -v "^Only in ant/com/google/inject/internal/cglib: beans$" | \
     grep -v "^Only in ant/com/google/inject/internal/cglib: transform$" | \
     grep -v "^Only in ant/com/google/inject/internal/cglib/transform: impl$" | \
     grep -v "^Only in ant/com/google/inject/internal/cglib: util$" | \
     grep -v "^Only in ant: net$" | \
     grep -v "^Only in ant: org$" | \
     grep -v "^Only in ant/com/google/inject/.*: package-info\.class$"
  # failure is 0 because we're using grep -v to filter things out
  if [ $? -eq 0 ]; then
    export RETVAL=1
  fi
  cd "$curdir"
  rm -rf "tmp$$"
}

## Only bother doing this on the jdk8/mvn build (before we publish snapshots).
## Otherwise it's a waste of time building mvn+ant each time.
if [ "$TRAVIS_JDK_VERSION" == "oraclejdk8" ] && \
   [ "$LABEL" == "mvn" ]; then
  echo "Cleaning and building ant & maven..."
  cleanAndBuild
  echo "Starting to compare jars..."
  echo
  findAndCompareJars
  if [ $RETVAL -eq 0 ]; then
    echo "Everything looks good!"
    exit 0
  else
    echo "Some things don't match -- see above for details."
    exit 1
  fi
fi
