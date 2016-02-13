Guice Bill of Materials (BOM)
============================
This BOM contains all the Guice modules in a single dependency management.

Use as follows:

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.myproject</groupId>
    <artifactId>myartifact</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.inject</groupId>
                <artifactId>guice-build</artifactId>
                <version>4.0.1-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    ...
</project>
```

For more information about [Dependency Management](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)