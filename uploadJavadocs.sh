#svn rm latest-javadoc javadoc
rm -rf latest-javadoc javadoc
#svn ci -m "Removed old Javadocs."
ant javadoc
mv build/docs/javadoc latest-javadoc
cp -r latest-javadoc javadoc
#svn add latest-javadoc javadoc
#svn ci -m "Added updated Javadocs."
