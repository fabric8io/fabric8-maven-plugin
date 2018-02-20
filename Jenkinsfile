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

            sh "mvn clean -B"

        } else if (utils.isCD()) {

            echo 'CD pipeline'

            //First we need to check that master is
            //stable and all tests are working properly
            //before generating tags and pushing it to github


            //These stage(), release(stagedProject) and
            //updateDownstreamDependencies(stagedProject)
            //are coming form release.groovy in same repo

           }
        }
    }
}
