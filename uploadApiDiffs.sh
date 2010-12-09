rm -rf build/docs
CV=3.0

# remove old api-diffs
svn rm latest-api-diffs/$CV
svn ci -m "Removed old $CV api diffs." latest-api-diffs/$CV

# create new api-diffs
ant jdiff
cp -r build/docs/latest-api-diffs latest-api-diffs/$CV
cp lib/build/jdiff/*.gif latest-api-diffs/$CV
mv latest-api-diffs/$CV/$CV.xml latest-api-diffs

# capture current javadoc snapshot
ant javadoc
cp -r build/docs/javadoc latest-api-diffs/$CV/javadoc

# commit changes
svn add latest-api-diffs/$CV
svn ci -m "Added updated $CV api diffs." latest-api-diffs/$CV latest-api-diffs/$CV.xml
