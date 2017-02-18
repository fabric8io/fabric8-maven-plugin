## fabric8-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-maven-plugin/)
[![Coverage](https://img.shields.io/sonar/https/sonarqube.com/io.fabric8:fabric8-maven-plugin-build/coverage.svg?style=flat-square)](https://sonarqube.com/overview?id=io.fabric8%3Afabric8-maven-plugin-build)
[![Technical Debt](https://img.shields.io/sonar/https/sonarqube.com/io.fabric8:fabric8-maven-plugin-build/tech_debt.svg?style=flat-square)](https://sonarqube.com/overview?id=io.fabric8%3Afabric8-maven-plugin-build)
[![Dependency Status](https://dependencyci.com/github/fabric8io/fabric8-maven-plugin/badge)](https://dependencyci.com/github/fabric8io/fabric8-maven-plugin)

<p align="center">
  <a href="http://fabric8.io/">
  	<img src="https://raw.githubusercontent.com/fabric8io/fabric8/master/docs/images/cover/cover_small.png" alt="fabric8 logo"/>
  </a>
</p>

This Maven plugin is a one-stop-shop for building and deploying Java applications for Docker, Kubernetes and OpenShift.

What to get started fast? Check out the [busy Java developers guide to developing microservices on Kubernetes and Docker](https://blog.fabric8.io/a-busy-java-developers-guide-to-developing-microservices-on-kubernetes-and-docker-98b7b9816fdf).

The full documentation can be found in the [User Manual](http://maven.fabric8.io) [[PDF](https://fabric8io.github.io/fabric8-maven-plugin/fabric8-maven-plugin.pdf)]. It supports the following goals:

| Goal                                          | Description                           |
| --------------------------------------------- | ------------------------------------- |
| [`fabric8:resource`](https://fabric8io.github.io/fabric8-maven-plugin/#fabric8:resource) | Create Kubernetes and OpenShift resource descriptors |
| [`fabric8:build`](https://fabric8io.github.io/fabric8-maven-plugin/#fabric8:build) | Build Docker images |
| [`fabric8:push`](https://fabric8io.github.io/fabric8-maven-plugin/#fabric8:push) | Push Docker images to a registry  |
| [`fabric8:deploy`](https://fabric8io.github.io/fabric8-maven-plugin/#fabric8:deploy) | Deploy Kubernetes / OpenShift resource objects to a cluster  |
| [`fabric8:watch`](https://fabric8io.github.io/fabric8-maven-plugin/#fabric8:watch) | Watch for doing rebuilds and restarts |

### Features

* Includes [docker-maven-plugin](https://github.com/fabric8io/docker-maven-plugin) for dealing with Docker images and hence inherits its flexible and powerful configuration.
* Supports both Kubernetes and OpenShift descriptors
* OpenShift Docker builds with a binary source (as an alternative to a direct image build agains a Docker daemon)
* Various configuration styles:
  * **Zero Config** for a quick ramp-up where opinionated defaults will be pre-selected.
  * **Inline Configuration** within the plugin configuration in an XML syntax
  * **External Configuration** templates of the real deployment descriptors which are enriched by the plugin.
* Flexible customization:
  * **Generators** analyze the Maven build and generated automatic Docker image configurations for certain systems (spring-boot, plain java, karaf ...)
  * **Enrichers** extend the Kubernetes / OpenShift resource descriptors by extra information like SCM labels and can add default objects like Services.
  * Generators and Enrichers can be individually configured and combined into *profiles*

### Documentation and Support

* [User Manual](http://maven.fabric8.io) [[PDF](https://fabric8io.github.io/fabric8-maven-plugin/fabric8-maven-plugin.pdf)]
* Examples are in the [samples](samples/) directory
* Many [fabric8 Quickstarts](https://github.com/fabric8-quickstarts) use this plugin and are good showcases, too.
* You'll find us in the [fabric8 community](http://fabric8.io/community/) and on IRC freenode in channel [#fabric8](https://webchat.freenode.net/?channels=fabric8) and we are happy to answer any questions.
* Contributions are highly appreciated and encouraged. Please send us Pull Requests.

### fabric8-maven-plugin 3 vs. 2

> This is a complete rewrite of the former fabric8-maven plugin. It does not share the same configuration syntax,
> but migration should be straight forward - please use the [fabric8:migrate goal from 2.x of the plugin](http://fabric8.io/guide/mavenFabric8Migrate.html). 
