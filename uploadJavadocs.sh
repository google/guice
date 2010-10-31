rm -rf build/docs
svn rm latest-javadoc javadoc
svn ci -m "Removed old Javadocs." latest-javadoc javadoc
ant javadoc
cp -r build/docs/javadoc latest-javadoc
cp -r latest-javadoc javadoc
mv build/docs/guice-*.xml lib/build
svn add latest-javadoc javadoc lib/build/guice-*.xml
svn ci -m "Added updated Javadocs." latest-javadoc javadoc lib/build/guice-*.xml
