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

set -e

echo ============================================
echo Deploying fabric8-maven-plugin documentation
echo ============================================

mvn install -DskipTests=true
cd doc
mvn -Phtml,pdf package
git clone -b gh-pages git@github.com/fabric8io/fabric8-maven-plugin.git gh-pages
cp -rv target/generated-docs/* gh-pages/
cd gh-pages
mv index.pdf fabric8-maven-plugin.pdf
git add --ignore-errors *
git commit -m "generated documentation"
git push origin gh-pages
cd ..
rm -rf gh-pages target
