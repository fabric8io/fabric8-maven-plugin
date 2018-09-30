# Changes

This document main purpose is to list changes which might affect backwards compatibility. It will not list all releases as fabric8-maven-plugin is build in a continous delivery fashion.

We use semantic versioning in some slight variation until our feature set has stabilized and the missing pieces has been filled in:

* The `MAJOR_VERSION` is kept to `3`
* The `MINOR_VERSION` changes when there is an API or configuration change which is not fully backward compatible.
* The `PATCH_LEVEL` is used for regular CD releases which add new features and bug fixes.

After this we will switch probably to real [Semantic Versioning 2.0.0](http://semver.org/)

###3.5-SNAPSHOT
* Fix 1021: Avoids empty deployment selector value in generated yaml resource
* Fix 1383: Check if instanceof Openshift before casting and handle failures.
* Fix 1390: Provides better exception message in case of invalid `application.yml` in Spring Boot

###3.5.41 (2018-08-01)
* Feature 1032: Improvements of the Vert.x Generator and enrichers
* Fix 1313: Removed unused Maven goals. Please contact us if something's missing for you.
* Fix 1299: autotls feature doesn't work with OpenShift 3.9 (Kubernetes 1.8+) due to InitContainer annotation deprecation
* Fix 1276: Proper inclusion of webapp's war regardless of the final name
* Feature: New 'path' config option for the webapp generator to set the context path
* Fix 1334: support for docker.pull.registry
* Fix 1268: Java console for OpenShift builds reachable again.
* Fix 1312: Container name should not be generated from maven group id
* Feature 917: Add `timeoutSeconds` configuration option for SpringBootHealthCheck enricher
* Fix 1073: Preserve file extension when copying file to helm chart folder
* Fix 1340: spring-boot-maven-plugin is detected under any groupId
* Fix 1346: karaf-maven-plugin is detected under any groupId

###4.0-SNAPSHOT
* Feature: Move to Java 1.8 as minimal requirement
* Refactor 1344: Removed unused Maven goals. Please contact us if something's missing for you.
* Refactor 949: Remove dependency from fabric8/fabric8
* Feature 1214: Don't use a default for skipBuildPom. This might break backwards compatibility, so please specify the desired value in case
* Fix 1093: Default tag for snapshot release is "latest", not the timestamp anymore
* Fix 1155 : Decouple regression test module from Fabric8 Maven Plugin
* Updated sample project versions to 4.0-SNAPSHOT
* Refactor 1370: Removed Jenkinsshift support
* Feature 1363: Added a Thorntail V2 sample for checking Jolokia/Prometheus issues
* Fix 894: Keep Service parameters stable after redeployment
* Fix 1330: Disable enrichers specific to the fabric8 platform by default
* Fix 1372: Filename to type mappings should be more flexible than a string array
* Fix 1327: Update docker maven plugin version
* Fix 1365: Generated image reference in deployment.yml contains duplicate registry name

###3.5.40
* Feature 1264: Added `osio` profile, with enricher to apply OpenShift.io space labels to resources
* Feature 1291: Added ImageStream triggers for StatefulSets, ReplicaSets and DaemonSets
* Feature 1293: Added support to create pullSecret in buildConfig when pulling from private registry in Openshift.
* Fix 1265: WildFly Swarm health check enricher now supports detecting MicroProfile Health
* Fix 1298: WildFly Swarm was renamed to Thorntail
* Fix 1284: Handle intermittent SocketTimeoutException while s2i build
* Fix Unzip Issue - https://github.com/fabric8io/fabric8-maven-plugin/pull/1303
* Bring Wildfly swarm documentation uptodate - https://github.com/fabric8io/fabric8-maven-plugin/pull/1297
* Upgraded Kubernetes Client to 3.2.0 - https://github.com/fabric8io/fabric8-maven-plugin/pull/1304
* Upgraded Fabric8 to 3.0.12 - https://github.com/fabric8io/fabric8-maven-plugin/pull/1307

###3.5.39
* Feature 1206: Added support for spring-boot 2 health endpoint
* Feature 1171: Added configuration options for delay and period on spring-boot health check probes
* Fix 1173: disable the Prometheus agent for WildFly Swarm applications, because it uses Java logging too early; also reenable the Jolokia agent, which was disabled due to the same problem but was fixed a long time ago
* Fix 1231: make helm artifact extension configurable with default value "tar.gz"
* Fix 1247: do not try to install non-existent imagestream yml file
* Fix 1185: K8s: resource fragment containing compute resources for containers triggers a WARNING.
* Fix 1237: When trimImageInContainerSpec is enabled, the generated yaml is incorrect
* Fix 1245: Use released version of Booster in place of master always in regression test
* Fix 1263: Display a warning in case of premature close of the build watcher by kubernetes client
* Fix 886: Introduce extends for profiles

###3.5.38
* Feature 1209: Added flag fabric8.openshift.generateRoute which if set to false will not generate route.yml and also will not add Route resource in openshift.yml. If set to true or not set, it will generate rou  te.yml and also add Route resource in openshift.yml. By default its value is true.
* Fix 1177: Added flag fabric8.openshift.enableAutomaticTrigger which would be able to enable/disable automatic deployments whenever there is new image
  generated.
* Fix 1184: MultiModule projects were not getting deployed using FMP 3.5.34 onwards. This was working after adding an extra flag which was breaking the previous behaviour in patch release. We will make these change again in minor release and will add the notes regarding that. For the time being, we have reverted the change.
* Fix #1226: Plugin fails to deploy changes to an application with S2I build. It used to pick first image verion always. This fix pick the right image tag by comaring created attribute of image tag

###3.5.35
* Fix 1130: Added flag fabric8.openshift.trimImageInContainerSpec which would set the container image reference to "", this is done to handle weird
  behavior of Openshift 3.7 in which subsequent rollouts lead to ImagePullErr.
* Feature 1174: ImageStreams use local lookup policy by default to simplify usage of Deployment or StatefulSet resources on Openshift

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
