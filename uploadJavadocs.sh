svn rm javadoc
svn ci -m "Removed old Javadocs."
#ant javadoc
mv build/javadoc .
svn add javadoc
svn ci -m "Added updated Javadocs."
