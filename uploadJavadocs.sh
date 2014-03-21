rm -rf build/docs
git rm -r latest-javadoc javadoc
ant javadoc
cp -r build/docs/javadoc latest-javadoc
cp -r latest-javadoc javadoc
mv build/docs/guice-*.xml lib/build
git add -f -A latest-javadoc javadoc lib/build/guice-*.xml
git commit -m "Added updated Javadocs." latest-javadoc javadoc lib/build/guice-*.xml

