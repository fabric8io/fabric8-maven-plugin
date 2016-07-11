echo ===========================================
echo Deploying docker-maven-plugin documentation
echo ===========================================

cd doc && \
mvn -Phtml,pdf package && \
git clone -b gh-pages git@github.com:fabric8io/fabric8-maven-plugin.git gh-pages && \
cp -rv target/generated-docs/* gh-pages/ && \
cd gh-pages && \
mv index.pdf fabric8-maven-plugin.pdf && \
git add --ignore-errors * && \
git commit -m "generated documentation" && \
git push origin gh-pages && \
cd .. && \
rm -rf gh-pages target
