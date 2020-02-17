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

trap 'exit' ERR

START_LINK=10
BASEDIR=$(dirname "$BASH_SOURCE")

function checkInput() {
  if [ "$#" -lt 1 ]; then
    echo -e "This script extracts chagnelog version contents from CHANGELOG.md"
    echo -e "Usage: ./extract-changelog-for-version.sh semVer [startLinkNumber]\n"
    echo -e "Must set a valid semantic version number (e.g. 1.3.37)"
    exit 1;
  fi
  dotCount=$(echo "$1" | tr -d -c '.' | wc -c)
  if [ "$dotCount" -ne 2 ]; then
      echo "Provided version has an invalid format, should be semver compliant (e.g. 1.3.37)"
      exit 1;
  fi
}

function extractChangelogPortion() {
  sed -e "/### ""$1""/,/###/!d" "$BASEDIR/../CHANGELOG.md"
}

function removeLastLine() {
  echo "$1" | sed '$d'
}

function replaceBullets() {
  echo -e "$1" | sed -e "s/^*/-/"
}

function addLinks() {
  lines=""
  links=""
  currentLink="$START_LINK"
  if [ -n "$2" ]; then currentLink="$2" ; fi
  while read -r line; do
    issueNumber=$(echo "$line" | sed -En 's/.*?#([0-9]+).*/\1/p')
    if [ -z "$issueNumber" ]; then
      lines+="$line\n";
    else
      lines+="$line [$currentLink]\n"
      links+="[$currentLink]: https://github.com/fabric8io/fabric8-maven-plugin/issues/$issueNumber\n"
      currentLink=$((currentLink + 1));
    fi
  done < <(echo "$1")
  echo -e "$lines\n$links";
}

function processChangelog() {
  changelog=$1
  changelog=$(extractChangelogPortion "$changelog")
  changelog=$(removeLastLine "$changelog")
  changelog=$(replaceBullets "$changelog")
  changelog=$(addLinks "$changelog" "$2")
  echo "$changelog";
}

checkInput "$@"
processChangelog "$@"
