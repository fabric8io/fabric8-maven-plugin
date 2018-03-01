## fabric8-maven-plugin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-maven-plugin/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-maven-plugin/)
[![Travis](https://secure.travis-ci.org/fabric8io/fabric8-maven-plugin.png)](http://travis-ci.org/fabric8io/fabric8-maven-plugin)
[![Circle CI](https://circleci.com/gh/fabric8io/fabric8-maven-plugin/tree/master.svg?style=shield)](https://circleci.com/gh/fabric8io/fabric8-maven-plugin/tree/master)
[![Coverage](https://sonarqube.com/api/badges/measure?key=io.fabric8:fabric8-maven-plugin-build&metric=coverage)](https://sonarqube.com/dashboard?id=io.fabric8%3Afabric8-maven-plugin-build)
[![Technical Debt](https://sonarqube.com/api/badges/measure?key=io.fabric8:fabric8-maven-plugin-build&metric=sqale_debt_ratio)](https://sonarqube.com/dashboard?id=io.fabric8%3Afabric8-maven-plugin-build)
[![Dependency Status](https://dependencyci.com/github/fabric8io/fabric8-maven-plugin/badge)](https://dependencyci.com/github/fabric8io/fabric8-maven-plugin)

<p align="center">
  <a href="http://fabric8.io/">
  	<img src="https://raw.githubusercontent.com/fabric8io/fabric8/master/docs/images/cover/cover_small.png" alt="fabric8 logo"/>
  </a>
</p>

### Introduction
This Maven plugin is a one-stop-shop for building and deploying Java applications for Docker, Kubernetes and OpenShift. It brings your Java applications on to Kubernetes and OpenShift. It provides a tight integration into maven and benefits from the build configuration already provided. It focuses on three tasks:
+ Building Docker images
+ Creating OpenShift and Kubernetes resources
+ Deploy application on Kubernetes and OpenShift

### Usage
To enable fabric8 maven plugin on your project just add this to the plugins sections of your pom.xml:

```
      <plugin>
        <groupId>io.fabric8</groupId>
        <artifactId>fabric8-maven-plugin</artifactId>
        <version>3.5.33</version>
      </plugin>
```

You can also do it using maven command like this : `mvn io.fabric8:fabric8-maven-plugin:<version>:setup`. You can check for the latest released version from the [maven central](http://search.maven.org/#search%7Cga%7C1%7Cfabric8-maven-plugin).

Now in order to use fabric8 maven plugin to build or deploy, make sure you have an OpenShift/Kubernetes cluster up and running. After making sure that, you can simply run your app in the cluster  :
```
      mvn fabric8:deploy
```
After you issue this command would start building resources and then deploy them to the running cluster. Below is a snippet of the build log on running spring-boot project in samples/spring-boot directory on MiniKube :
```
[INFO] --- fabric8-maven-plugin:3.5-SNAPSHOT:build (default) @ fabric8-maven-sample-spring-boot ---
[INFO] F8: Building Docker image in Kubernetes mode
[INFO] F8: Running generator spring-boot
[INFO] F8: spring-boot: Using Docker image fabric8/java-jboss-openjdk8-jdk:1.2 as base / builder
[INFO] Copying files to /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/docker/fabric8/fabric8-maven-sample-spring-boot/snapshot-171218-140833-0299/build/maven
[INFO] Building tar: /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/docker/fabric8/fabric8-maven-sample-spring-boot/snapshot-171218-140833-0299/tmp/docker-build.tar
[INFO] F8: [fabric8/fabric8-maven-sample-spring-boot:snapshot-171218-140833-0299] "spring-boot": Created docker-build.tar in 150 milliseconds
[INFO] F8: [fabric8/fabric8-maven-sample-spring-boot:snapshot-171218-140833-0299] "spring-boot": Built image sha256:d83ec
[INFO] F8: [fabric8/fabric8-maven-sample-spring-boot:snapshot-171218-140833-0299] "spring-boot": Tag with latest
[INFO]
[INFO] --- fabric8-maven-plugin:3.5-SNAPSHOT:helm (default) @ fabric8-maven-sample-spring-boot ---
[WARNING] F8: Chart source directory /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/classes/META-INF/fabric8/k8s-template does not exist so cannot make chart fabric8-maven-sample-spring-boot. Probably you need run 'mvn fabric8:resource' before.
[INFO]
[INFO] --- maven-install-plugin:2.5.2:install (default-install) @ fabric8-maven-sample-spring-boot ---
[INFO] Installing /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT.jar to /home/rohan/.m2/repository/io/fabric8/fabric8-maven-sample-spring-boot/3.5-SNAPSHOT/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT.jar
[INFO] Installing /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/pom.xml to /home/rohan/.m2/repository/io/fabric8/fabric8-maven-sample-spring-boot/3.5-SNAPSHOT/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT.pom
[INFO] Installing /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/classes/META-INF/fabric8/openshift.yml to /home/rohan/.m2/repository/io/fabric8/fabric8-maven-sample-spring-boot/3.5-SNAPSHOT/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT-openshift.yml
[INFO] Installing /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/classes/META-INF/fabric8/openshift.json to /home/rohan/.m2/repository/io/fabric8/fabric8-maven-sample-spring-boot/3.5-SNAPSHOT/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT-openshift.json
[INFO] Installing /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/classes/META-INF/fabric8/kubernetes.yml to /home/rohan/.m2/repository/io/fabric8/fabric8-maven-sample-spring-boot/3.5-SNAPSHOT/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT-kubernetes.yml
[INFO] Installing /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/classes/META-INF/fabric8/kubernetes.json to /home/rohan/.m2/repository/io/fabric8/fabric8-maven-sample-spring-boot/3.5-SNAPSHOT/fabric8-maven-sample-spring-boot-3.5-SNAPSHOT-kubernetes.json
[INFO]
[INFO] <<< fabric8-maven-plugin:3.5-SNAPSHOT:deploy (default-cli) < install @ fabric8-maven-sample-spring-boot <<<
[INFO]
[INFO]
[INFO] --- fabric8-maven-plugin:3.5-SNAPSHOT:deploy (default-cli) @ fabric8-maven-sample-spring-boot ---
[INFO] F8: Using Kubernetes at https://192.168.99.100:8443/ in namespace default with manifest /home/rohan/work/repos/fabric8-maven-plugin/samples/spring-boot/target/classes/META-INF/fabric8/kubernetes.yml
[INFO] Using namespace: default
[INFO] Creating a Service from kubernetes.yml namespace default name fabric8-maven-sample-spring-boot
[INFO] Created Service: target/fabric8/applyJson/default/service-fabric8-maven-sample-spring-boot-1.json
[INFO] Using namespace: default
[INFO] Creating a Deployment from kubernetes.yml namespace default name fabric8-maven-sample-spring-boot
[INFO] Created Deployment: target/fabric8/applyJson/default/deployment-fabric8-maven-sample-spring-boot-1.json
[INFO] F8: HINT: Use the command `kubectl get pods -w` to watch your pods start up
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 12.057 s
[INFO] Finished at: 2017-12-18T14:08:41+05:30
[INFO] Final Memory: 53M/488M
[INFO] ------------------------------------------------------------------------

```
After build is finished, your application pod gets created:
```
~/work/repos/fabric8-maven-plugin/samples/spring-boot : $ kubectl get pods
NAME                                                READY     STATUS    RESTARTS   AGE
fabric8-maven-sample-spring-boot-6c865777d6-qxdmd   0/1       Running   0          5s
```

Want to get started fast? Check out the [busy Java developers guide to developing microservices on Kubernetes and Docker](https://blog.fabric8.io/a-busy-java-developers-guide-to-developing-microservices-on-kubernetes-and-docker-98b7b9816fdf).

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
  * **Zero Configuration** for a quick ramp-up where opinionated defaults will be pre-selected.
  * **Inline Configuration** within the plugin configuration in an XML syntax.
  * **External Configuration** templates of the real deployment descriptors which are enriched by the plugin.
  * **Docker Compose Configuration** provide Docker Compose file and bring up docker compose deployments on a Kubernetes/OpenShift cluster.
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

### Tests we run for every new Pull Request
There are the links of the CircleCI and Jenkins for the tests which run for every new Pull Request. You can view all the recent builds also.

* [Travis: build](http://travis-ci.org/fabric8io/fabric8-maven-plugin)
* [CircleCI: Regression Tests](https://circleci.com/gh/fabric8io/fabric8-maven-plugin/tree/master)
* [Jenkins: Unit Tests](https://ci.fabric8.io/job/fabric8-maven-plugin-pullreq/)
