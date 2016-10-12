#!/usr/bin/groovy
def repo(){
 return 'fabric8io/fabric8-maven-plugin'
}

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
  pushPomPropertyChangePR {
    propertyName = 'fabric8.maven.plugin.version'
    projects = [
            'fabric8io/fabric8-maven-dependencies',
            'fabric8io/fabric8-devops',
            'fabric8io/fabric8-platform',
            'fabric8io/fabric8-ipaas',
            'fabric8io/ipaas-platform'
    ]
    version = stagedProject[1]
  }

  pushPomPropertyChangePR {
    parentPomLocation = 'parent/pom.xml'
    propertyName = 'fabric8.maven.plugin.version'
    projects = [
            'fabric8io/funktion',
            // this is for the docs!
            'fabric8io/fabric8-maven-plugin'
    ]
    version = stagedProject[1]
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
