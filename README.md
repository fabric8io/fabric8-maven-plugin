## fabric8-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-maven-plugin/)
.org/overview?id=io.fabric8%3Afabric8-maven-plugin-build)
[![Technical Debt](https://img.shields.io/sonar/https/nemo.sonarqube.org/io.fabric8:fabric8-maven-plugin-build/tech_debt.svg)](https://nemo.sonarqube.org/overview?id=io.fabric8%3Afabric8-maven-plugin-build)
[![Dependency Status](https://www.versioneye.com/java/io.fabric8:fabric8-maven-plugin-build/badge?style=flat)](https://www.versioneye.com/java/io.fabric8:fabric8-maven-plugin-build/)

> **This is work in progress. It is a rewrite of the original
>  [fabric8-maven-plugin](https://github.com/fabric8io/fabric8/tree/master/fabric8-maven-plugin)
>  which includes
>  [docker-maven-plugin](https://github.com/fabric8io/docker-maven-plugin)
>  and is intended a one-stop-shop for building and deploying Java
>  applications for Docker, Kubernetes and OpenShift**
>
> It's not even alpha yet.

This plugins makes it easy to get your Java applications on to
OpenShift or Kubernetes. The main goal is easy of use and a consistent
usage pattern for different build workflows.

It supports two configuration strategies:

* **Inline Configuration** which happens within the plugin
  configuration itself. All deployment artifacts like Dockerfiles or
  Kubernetes resource descriptors are created completely from this
  configuration. This strategy is best suited for a quick ramp-up and
  simple use cases with the minimal need of extra configuration.
  
* **External Configuration** uses templates of the real deployment
  descriptors which is enriched by build information from the POM. For
  example LabelEnricher can add to the label metadata of Kubernetes
  resource objects. External configuration is for supporting complex
  setups with all possibilities of the original descriptors.
  
This approach is already used in
[docker-maven-plugin](https://github.com/fabric8io/docker-maven-plugin)
with quite some success.

This plugin supports multiple goals, however at the end it boils down
to two main Maven goals:

* `fabric8:build` will build the artefacts required for the target
  platform. Depending on the selected *build strategy* this can be a
  Docker image for Kubernetes or an OpenShift `BuildConfig` and
  `Build` object which create `ImageStream`s. 
  
* `fabric8:deploy` will create the resource objects on
  Kuberentes / OpenShift to run the application. Depending on the
  configured *deployment strategy* this can be `Templates` for
  OpenShift or plain `ReplicationController`, `Services`, ... for a
  direct deployment.
  
In addition there are other support goals like `fabric8:watch`,
`fabric8:tool#create-routes`, `fabric8:tool#helm` ....

----

For enriching resource descriptors with e.g. meta information like git
commit ids or icon URL the plugin uses a dynamic service lookup from
the classpath so that it is easy to chose the kind of *enricher*
needed. fabric8-maven-plugin comes with a default set of enricher
which makes it easy to use the generated applications with other
fabric8 components like DevOps or the fabric8 console. 

----

It turned out that refactoring the current
[fabric8-maven-plugin](https://github.com/fabric8io/fabric8/tree/master/fabric8-maven-plugin)
is quite a challenge, especially because of its size and
complexity. Also its not sure whether all of (partly experimental)
features are still needed. 

Therefore in order to allow a clean separation of basic functionality
and more opinionated enrichement for the very beginning, this plugin
starts nearly from scratch and adds missing features while we go.
