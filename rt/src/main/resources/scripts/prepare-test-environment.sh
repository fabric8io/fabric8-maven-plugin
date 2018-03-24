#!/bin/sh

#install kubernetes and openshift CLI tools
kube_version=$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)
curl -LO https://storage.googleapis.com/kubernetes-release/release/${kube_version}/bin/linux/amd64/kubectl && \
    chmod +x kubectl && sudo mv kubectl /usr/local/bin/
echo "Installed kubectl CLI tool"


echo "Installing nsenter"
if ! which nsenter > /dev/null; then
  echo "Did not find nsenter. Installing it."
  NSENTER_BUILD_DIR=$(mktemp -d /tmp/nsenter-build-XXXXXX)
  pushd ${NSENTER_BUILD_DIR}
  curl https://www.kernel.org/pub/linux/utils/util-linux/v2.31/util-linux-2.31.tar.gz | tar -zxf-
  cd util-linux-2.31
  ./configure --without-ncurses
  make nsenter
  sudo cp nsenter /usr/local/bin
  rm -rf "${NSENTER_BUILD_DIR}"
  popd
fi
if ! which systemd-run > /dev/null; then
  echo "Did not find systemd-run. Hacking it to work around Kubernetes calling it."
  echo '#!/bin/bash
  echo "all arguments: "$@
  while [[ $# -gt 0 ]]
  do
    key="$1"
    if [[ "${key}" != "--" ]]; then
      shift
      continue
    fi
    shift
    break
  done
  echo "remaining args: "$@
  exec $@' | sudo tee /usr/bin/systemd-run >/dev/null
  sudo chmod +x /usr/bin/systemd-run
fi

oc_tool_version="openshift-origin-client-tools-${OC_VERSION}-${COMMIT_ID}-linux-64bit"
curl -LO https://github.com/openshift/origin/releases/download/${OC_VERSION}/${oc_tool_version}.tar.gz && \
    tar -xvzf ${oc_tool_version}.tar.gz && chmod +x $PWD/${oc_tool_version}/oc && sudo mv $PWD/${oc_tool_version}/oc /usr/local/bin/ && \
    rm -rf ${oc_tool_version}.tar.gz
echo "Installed OC CLI tool"

#add insecure docker registry
tmp=`mktemp`
echo 'DOCKER_OPTS="$DOCKER_OPTS --insecure-registry 172.30.0.0/16"' > ${tmp}
sudo mv ${tmp} /etc/default/docker
sudo mount --make-shared /
sudo service docker restart
echo "Configured Docker daemon with insecure-registry"

#make OpenShift up & running
oc cluster up --version=${OC_VERSION}
sleep 10
oc login -u developer -p developer
echo "Configured OpenShift cluster : ${OC_VERSION}"
