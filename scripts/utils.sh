#!/bin/bash
#
# Copyright 2016 Red Hat, Inc.
#
# Red Hat licenses this file to you under the Apache License, version
# 2.0 (the "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied.  See the License for the specific language governing
# permissions and limitations under the License.
#


function get_project_version() {
    project_version=$(mvn -q \
    -Dexec.executable="echo" \
    -Dexec.args='${project.version}' \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)
    echo $project_version
}

function setup_git_ssh() {
    git config --global user.email fabric8cd@gmail.com
    git config --global user.name fabric8cd
}

function push_tag() {
    release_version=$1
    git tag -fa v${release_version} -m 'Release version ${release_version}'
    git push origin v${release_version}
}

# check the logic of this for getting repo_id
function get_repo_ids() {
  find target/  -maxdepth 1 -name "*.properties" > target/repos.txt
  filename=$(cat target/repos.txt)
  repo_id=$filename
  echo $repo_id
  exit 0
}

# check this
function stage_sonatype_repo() {
    mvn clean -B
    mvn -V -B -e -U install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:deploy -P release -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging
    get_repo_ids
}

function stage_project() {
    setup_git_ssh
    if [ -z $GH_USER ]; then
        GH_USER=fabric8cd
        echo $GH_USER
    fi
    git remote set-url origin https://${GH_USER}:${GH_TOKEN}@github.com/${project}.git

    version=$(get_project_version)
    
    if [ -z $RELEASE_VERSION && -z $version ]; then
        echo "NO RELEASE VERSION SET IN ENV AND POM! Exiting..."
        exit 1
    fi

    if [ -z $version]; then
        version=$RELEASE_VERSION
    fi    
    
    mvn versions:set -DnewVersion=$version
    repo_ids=$(stage_sonatype_repo)
    push_tag $version
    echo "$project,$version,$repo_ids"
}

# check how to handle if there is a failure in the release
function release_sonatype_repo() {
    repo_id=$1
    mvn -B org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=${repo_id} -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60
}