echo ============================================
echo Deploying fabric8-maven-plugin documentation
echo ============================================

cd doc && \
mvn -Phtml,pdf package && \
git clone -b gh-pages https://rhuss:${GITHUB_TOKEN}@github.com/fabric8io/fabric8-maven-plugin.git gh-pages && \
git config --global user.email "travis@fabric8.io" && \
git config --global user.name "Travis" && \
cp -rv target/generated-docs/* gh-pages/ && \
cd gh-pages && \
mv index.pdf fabric8-maven-plugin.pdf && \
git add --ignore-errors * && \
git commit -m "generated documentation" && \
git push origin gh-pages && \
cd .. && \
rm -rf gh-pages target
