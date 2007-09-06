svn rm latest-javadoc
svn ci -m "Removed old Javadocs."
ant javadoc
mv build/javadoc latest-javadoc
svn add latest-javadoc
svn ci -m "Added updated Javadocs."
