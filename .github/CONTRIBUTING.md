# Contributing

Do you want to contribute? Awesome! We **♥︎♥︎ LOVE ♥︎♥︎** contributions ;-)

So basically you can contribute to our project in the following ways:
## Development
There are lots of issues that are on our [issues](https://github.com/fabric8io/fabric8-maven-plugin/issues) page. You can have a look at it and decide over what you want to fix/contribute towards. These are some points regarding getting started with development of fabric8 maven plugin:

     * Getting Source
    
        git clone https://github.com/fabric8io/fabric8-maven-plugin.git
    
     * How to build and run project
     In order to build this project, you can simply run:
     
        mvn clean install
    
     Above command would run both unit and integration tests. But If you want to skip tests, simply use skipTests
     flag like this:
    
        mvn clean install -DskipTests 
    
     After this you can use any of the sample project provided in $FMP/samples/ directory. Remember to change the
     project's fabric8 maven plugin's version if it's not set to current project version.

     * Debugging
     Use this command to open up a debug port that can be connected to via any client(typically Eclipse/IntelliJ)
     for debugging purposes:
     
        mvnDebug clean install
    
     This command can be used to debug specific goals of fabric8 maven plugin also, like fabric8:build,
     fabric8:deploy etc. Use them accordingly.

## Testing
Are you facing any problem with this project? Feel free to report any issue on our issues page. Or Are you interested in adding some test support(in which we need lots of help)? You can help us in adding more coverage for the current codebase or maybe provide us with a nice integration test.

## Documentation
If you find any typo in online documentation or maybe you think there could be some improvement in current documentation, feel free to get in touch with us on IRC/Github.


## CONTRIBUTION GUIDELINES

Here some things to check out when doing a PR:

* If adding a new feature please [update the documentation](https://github.com/fabric8io/fabric8-maven-plugin/tree/master/doc/src/main/asciidoc), too.
* Don't forget the unit tests.
* Make sure you add the license headers at top of every new source file you add while implementing feature.
* Spaces only, please!

However, if you can't do some of the points above, please still consider contributing. Simply ask us on `#fabric8` at Freenode or via a GitHub [issue](https://github.com/fabric8io/fabric8-maven-plugin/issues). We are not dogmatic.
