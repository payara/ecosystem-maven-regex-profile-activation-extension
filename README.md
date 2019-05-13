# Regex Profile Activation Extension

This maven extension allows activating profiles by checking that a property matches a given regex, rather than an exact value.

## Example Usage

You can register this extension with your maven build by specifying it in your `${project.baseir}/.mvn/extensions.xml` file like so:

~~~
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">

    <extension>
        <groupId>fish.payara.maven.extensions</groupId>
        <artifactId>regex-profile-activator</artifactId>
        <version>0.2</version>
    </extension>

</extensions>
~~~

This extension is now active in your build, and you can activate profiles using a regex string beginning and ending with a `/`.

~~~
<profile>
    <id>test-profile</id>
    <activation>
        <property>
            <name>myproperty</name>
            <value>/abra.+/</value>
        </property>
    </activation>
</profile>
~~~

The profile above will be active when built with `-Dmyproperty=abracadabra`, or anything else matching the regex.

## Details

This extension will find properties first by checking for properties specified on the command line with `-D`, followed by other maven system properties or environment variables. If none of these are found, the property will be read from discovered pom files in the project hierarchy, checking each parent pom recursively until no more are found.

## Releasing

In order to release, you will need to sign the source, javadoc and artifact JARs. To do this, build the project with the `sign` profile:

~~~
mvn install -Dsign
~~~