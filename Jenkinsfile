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
@Library('github.com/fabric8io/fabric8-pipeline-library@master')
def utils = new io.fabric8.Utils()
clientsTemplate{
    mavenNode {
        checkout scm
        readTrusted 'release.groovy'
        sh "git remote set-url origin git@github.com:fabric8io/fabric8-maven-plugin.git"

        def pipeline = load 'release.groovy'

        if (utils.isCI()) {

            echo 'CI pipeline'

            sh "mvn clean install"

        } else if (utils.isCD()) {

            //First we need to check that master is
            //stable and all tests are working properly
            //before generating tags and pushing it to github

            sh "mvn clean -B"

            sh "mvn clean verify"

            def stagedProject

            //These stage(), release(stagedProject) and
            //updateDownstreamDependencies(stagedProject)
            //are coming form release.groovy in same repo

            stage('Stage') {

                // This will generate new tag, push them to github,
                // create the new release branch, build and run test

                stagedProject = pipeline.stage()
            }

            stage('Promote') {

                //This will release the project and update on
                //maven central

                pipeline.release(stagedProject)
            }

            // Disabled for now as it probably doesn't work because of the different directory structure
            // with a dedicated doc-module
            //stage 'Website'
            //pipeline.website(stagedProject)

            stage('Update downstream dependencies') {

                //This will update the version of fmp
                //in the pom.xml of some downstream repos like
                //fabric8-services, quickstart etc.
                pipeline.updateDownstreamDependencies(stagedProject)
            }
        }
    }
}


deployTemplate{
  dockerNode {
    stage 'Deploy and run system tests'
    deployAndRunSystemTests()
  }
}

def deployAndRunSystemTests() {

    //This will build the downstream projects
    // (may be not all projects)
    // to check whether the new version is
    // working fine for them

    def fabric8Quickstarts
    def fabric8Devops
    def fabric8Forge
    def yaml

    stage 'build snapshot fabric8-devops'
    ws('devops') {
        git 'https://github.com/fabric8io/fabric8-devops.git'
        def pipeline = load 'release.groovy'
        fabric8Devops = mavenBuildSnapshot {
            extraImagesToStage = pipeline.externalImages()
        }
    }

    stage 'build snapshot quickstarts'
    ws('quickstarts') {
        git 'https://github.com/fabric8io/ipaas-quickstarts.git'
        fabric8Quickstarts = mavenBuildSnapshot {}
    }

    stage 'build snapshot fabric8-forge'
    ws('forge') {
        git 'https://github.com/fabric8io/fabric8-forge.git'
        fabric8Forge = mavenBuildSnapshot {
            pomVersionToUpdate = ['fabric8.devops.version': fabric8Devops, 'fabric8.archetypes.release.version': fabric8Quickstarts]
        }
    }

    stage 'build snapshot fabric8-platform'
    ws('platform') {
        git 'https://github.com/fabric8io/fabric8-platform.git'
        mavenBuildSnapshot {
            pomVersionToUpdate = ['fabric8.devops.version': fabric8Devops, 'fabric8.forge.version': fabric8Forge]
        }
        yaml = readFile file: "packages/fabric8-platform/target/classes/META-INF/fabric8/kubernetes.yml"

        if (yaml == null) {
            error 'no yaml found for fabric8 platform'
        }
    }

    stage 'starting system tests'
    fabric8SystemTests {
        packageYAML = yaml
    }
}
