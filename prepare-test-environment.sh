#!/bin/sh

#add insecure docker registry
tmp=`mktemp`
echo 'DOCKER_OPTS="$DOCKER_OPTS --insecure-registry 172.30.0.0/16"' > ${tmp}
sudo mv ${tmp} /etc/default/docker
sudo mount --make-shared /
sudo service docker restart

#install kubernetes and openshift CLI tools
#kube_release=$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)
#curl -LO https://storage.googleapis.com/kubernetes-release/release/${kube_release}/bin/linux/amd64/kubectl && \
    #chmod +x kubectl && sudo mv kubectl /usr/local/bin/

#client_tools="openshift-origin-client-tools-${OC_VERSION}-${COMMIT_ID}-linux-64bit"
#curl -LO https://github.com/openshift/origin/releases/download/${OC_VERSION}/${client_tools}.tar.gz && \
    #tar -xvzf ${client_tools}.tar.gz && chmod +x $PWD/${client_tools}/oc && sudo mv $PWD/${client_tools}/oc /usr/local/bin/ && \
    #rm -rf ${client_tools}.tar.gz

#make OpenShift up & running
oc cluster up --version=${OC_VERSION}
sleep 10
oc login -u system:admin
