#!/bin/sh

if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
  # Run SonarQube analysis
  mvn -Pjacoco package sonar:sonar \
      -Dsonar.host.url=https://sonarcloud.io \
      -Dsonar.login=${SONARQUBE_TOKEN}
fi
