# Changes

This document main purpose is to list changes which might affect backwards compatibility. It will not list all releases as fabric8-maven-plugin is build in a continous delivery fashion.

We use semantic versioning in some slight variation until our feature set has stabilized and the missing pieces has been filled in:

* The `MAJOR_VERSION` is kept to `3`
* The `MINOR_VERSION` changes when there is an API or configuration change which is not fully backward compatible.
* The `PATCH_LEVEL` is used for regular CD releases which add new features and bug fixes.

After this we will switch probably to real [Semantic Versioning 2.0.0](http://semver.org/)

###3.5.38
* Feature 1209: Added flag fabric8.openshift.generateRoute which if set to false will not generate route.yml and also will not add Route resource in openshift.yml. If set to true or not set, it will generate rou  te.yml and also add Route resource in openshift.yml. By default its value is true.

###3.5.35
* Fix 1130: Added flag fabric8.openshift.trimImageInContainerSpec which would set the container image reference to "", this is done to handle weird
  behavior of Openshift 3.7 in which subsequent rollouts lead to ImagePullErr.
* Feature 1174: ImageStreams use local lookup policy by default to simplify usage of Deployment or StatefulSet resources on Openshift
* Fix 1177: Added flag fabric8.openshift.enableAutomaticTrigger which would be able to enable/disable automatic deployments whenever there is new image
  generated.

###3.5.34
* Feature 1003: Added suspend option to remote debugging
* Remove duplicate tenant repos from downstream version updates and add in tjenkins platform
* Fix 1051: resource validation was slow due to online hosted schema. The fix uses the JSON schema from kubernetes-model project
* Fix 1062: Add a filter to avoid duplicates while generating kubernetes template(picking the local generated resource ahead of any dependency). Added a resources/ folder in enricher/standard/src/test/ directory to add some sample yaml and jar resource files for DependencyEnricherTest.
* Fix 1042: Added a fabric8.build.switchToDeployment option to switch to Deployments rather than DeploymentConfig provided ImageStreams are not used on OpenShift. If value is set to true then fabric8-maven-plugin would switch to deployments, default value is false.

### 3.3.0

* The base image for Docker based builds (fabric8.mode == Kubernetes) has changed from fabric8/java-alpine-opendjdk8-jdk to fabric8/java-jboss-openjdk8-jdk which is CentOS based. Reason for this were issues with DNS lookups on Alpine. As before you always can change the base image with `-Dfabric8.from`.

### 3.2.1 (2016-11-17)

* Changed the base generator configuration `<enabled>` to `<add>` as it means to add this generator's image when it applies in contrast to only run when there is no other image configuration yet.
* Changed the default directories which are picked up the `java-exec` generator to `src/main/fabric8-includes` for extra file to be added to a Docker image.
* In the karaf and java-exec generator configuration `baseDir` changed to `targetDir` for specifying the target directory within the image where to put things into. This is in alignment with the docker-maven-plugin.
* In the webapp-generator configuration `deploymentDir` changed to `targetDir` for consistencies sake.
