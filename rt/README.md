# Fabric8 Maven Plugin Regression tests

The core directory for fabric8-maven-plugin regression tests

These tests are intended to test the overall integrity of fabric8-maven-
plugin in the fabric8 ecosystem. In case anything breaking in fabric8 
maven plugin, it would be a bit easier for the maintainers to find out
 what went wrong. So the basic flow of all these tests is following:

* Clone the sample project using fabric8 maven plugin.
* Update it's pom version to latest SNAPSHOT version so that we test 
  local version of plugin.
* Run a fabric8:deploy goal to deploy the application on top of 
  OpenShift cluster.
* Query the application running inside the cluster, using REST call 
  or maybe by plain Kubernetes Client(asserting resources, labels etc).
* Redeploy the application and re-test; to check redeployment scenario.

## Travis CI Setup for regression-tests
These tests only run in a particular build profile( See rt/pom.xml).These
 are intended to run on each pull request. So we basically spin off a 
 live OpenShift cluster in order to test. See .travis.yml in project's
root directory, for Travis CI setup which basically invokes a shell script
in each of it's job, to spin of various versions of OpenShift(namely 3.6.0,
3.6.1, 3.7.2) for tests.

## Future Work
* Improve build time of these regression tests
* Add support for Openshift v3.9.x
* Add support for newer stable Openshift versions
