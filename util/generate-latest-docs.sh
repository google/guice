echo -e "Generating latest javadoc & JDiff...\n"

if [-d "build/docs"]; then
  rm -r build/docs
fi

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
