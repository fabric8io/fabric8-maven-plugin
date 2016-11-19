# Changes

This document main purpose is to list changes which might affect backwards compatibility. It will not list all releases as fabric8-maven-plugin is build in a continous delivery fashion.

We use semantic versioning in some slight variation until our feature set has stabilized and the missing pieces has been filled in:

* The `MAJOR_VERSION` is kept to `3`
* The `MINOR_VERSION` changes when there is an API or configuration change which is not fully backward compatible. 
* The `PATCH_LEVEL` is used for regular CD releases which add new features and bug fixes. 

After this we will switch probably to real [Semantic Versioning 2.0.0](http://semver.org/)

### 3.2.1 (2016-11-17)

* Changed the base generator configuration `<enabled>` to `<add>` as it means to add this generator's image when it applies in contrast to only run when there is no other image configuration yet.
* Changed the default directories which are picked up the `java-exec` generator to `src/main/fabric8-includes` for extra file to be added to a Docker image. 
* In the karaf and java-exec generator configuration `baseDir` changed to `targetDir` for specifying the target directory within the image where to put things into. This is in alignment with the docker-maven-plugin.
* In the webapp-generator configuration `deploymentDir` changed to `targetDir` for consistencies sake.
