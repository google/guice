rm -rf build/docs
CV=4.0

# remove old api-diffs
git rm -r latest-api-diffs/$CV

# create new api-diffs
ant jdiff
cp -r build/docs/latest-api-diffs latest-api-diffs/$CV
cp lib/build/jdiff/*.gif latest-api-diffs/$CV
mv latest-api-diffs/$CV/$CV.xml latest-api-diffs

# capture current javadoc snapshot
ant javadoc
cp -r build/docs/javadoc latest-api-diffs/$CV/javadoc

# commit changes
git add -A latest-api-diffs
echo git commit -m "Added updated $CV api diffs."  latest-api-diffs
