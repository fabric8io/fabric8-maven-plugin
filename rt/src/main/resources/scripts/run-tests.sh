#!/bin/sh

echo "working directory"
pwd
echo "TRAVIS_BUILD_DIR"
echo $TRAVIS_BUILD_DIR

cd $TRAVIS_BUILD_DIR/rt
mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B install -P regression-test
