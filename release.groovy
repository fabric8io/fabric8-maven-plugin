#!/usr/bin/groovy
/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

def repo(){
 return 'fabric8io/fabric8-maven-plugin'
}

//This is execute the call() from stageProject.groovy in
//https://github.com/fabric8io/fabric8-pipeline-library/vars

//same applies for other functions

def stage(){
  return stageProject{
    project = repo()
    useGitTagForNextVersion = true
    setVersionExtraArgs = '-pl parent'
  }
}

def updateDependencies(source){
  def properties = []
  properties << ['<version.fabric8>','io/fabric8/kubernetes-api']
  properties << ['<version.docker-maven-plugin>','io/fabric8/docker-maven-plugin']

  updatePropertyVersion{
    updates = properties
    repository = source
    project = repo()
  }
}

def updateDownstreamDependencies(stagedProject) {
  def newVersion = stagedProject[1]

  pushPomPropertyChangePR {
    propertyName = 'fabric8.maven.plugin.version'
    projects = [
            'fabric8-services/fabric8-tenant-che',
            'fabric8-services/fabric8-tenant-jenkins',
            'fabric8-services/fabric8-tenant-team',
            'fabric8-jenkins/fabric8-jenkins-platform'
    ]
    version = newVersion
    autoMerge = true
  }

  pushPomPropertyChangePR {
    propertyName = 'fabric8.maven.plugin.version'
    projects = [
            'fabric8io/fabric8-maven-dependencies',
            'fabric8io/fabric8-devops',
            'fabric8io/fabric8-platform',
            'fabric8io/fabric8-generator',
            'fabric8io/fabric8-ipaas',
            'funktionio/funktion-connectors',
            'fabric8-quickstarts/spring-boot-webmvc', // these are used in the system tests in a later stage so we
            'fabric8-quickstarts/spring-boot-camel-xml' // need to make sure their deps are updated before the quickstart archetypes are generated
            // 'obsidian-toaster/generator-backend' currently we can only create PRs on repos where fabric8cd has karma to creater a branch and make the PR from
    ]
    version = newVersion
  }
  pushPomPropertyChangePR {
    propertyName = 'version.fabric8-maven-plugin'
    projects = [
            'obsidian-toaster/obsidian-addon'
    ]
    version = newVersion
  }

  pushPomPropertyChangePR {
    parentPomLocation = 'parent/pom.xml'
    propertyName = 'fabric8.maven.plugin.version'
    projects = [
            // this is for the docs!
            'fabric8io/fabric8-maven-plugin'
    ]
    version = newVersion
  }
}

def approveRelease(project){
  def releaseVersion = project[1]
  approve{
    room = null
    version = releaseVersion
    console = null
    environment = 'fabric8'
  }
}

def release(project){
  releaseProject{
    stagedProject = project
    useGitTagForNextVersion = true
    helmPush = false
    groupId = 'io.fabric8'
    githubOrganisation = 'fabric8io'
    artifactIdToWatchInCentral = 'fabric8-maven-plugin'
    artifactExtensionToWatchInCentral = 'jar'
  }
}

def mergePullRequest(prId){
  mergeAndWaitForPullRequest{
    project = repo()
    pullRequestId = prId
  }

}
return this;
