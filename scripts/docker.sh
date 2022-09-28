#!/usr/bin/env bash

###############################################################################
# README
###############################################################################
# This script contains a bunch of helper functions for some docker operations.
###############################################################################
###############################################################################


###############################################################################
# Get helper functions.
###############################################################################
# shellcheck disable=SC1091
. scripts/helper_functions.sh;
###############################################################################
###############################################################################

# Helper command to check the available version of the docker command.
function checkAvailableVersion() {
  # shellcheck disable=SC2086,SC2010
  ls ${PATH//:/ } 2> /dev/null | grep -i docker 2> /dev/null || true;
  docker --version;
}

# Helper command to perform login in dockerhub (https://hub.docker.com/) via docker command.
# The parameters are:
# * DOCKERHUB_USERNAME
# * DOCKERHUB_PASSWORD
function loginDockerHub() {
  echo "${2}" | docker login -u "${1}" --password-stdin;
}

# Helper command to build the MobileRT docker image.
# It builds the image in release mode.
# The parameters are:
# * BASE_IMAGE
# * BRANCH
# * VERSION
function buildDockerImage() {
  prepareBinaries;
  du -h -d 1 scripts;
  docker build \
    -t ptpuscas/mobile_rt:"${3}" \
    -f docker_image/Dockerfile \
    --no-cache=false \
    --build-arg BASE_IMAGE="${1}" \
    --build-arg BRANCH="${2}" \
    --build-arg BUILD_TYPE=release \
    .;
}

# Helper command to pull the MobileRT docker image.
# The parameters are:
# * VERSION
function pullDockerImage() {
  local output;
  exec 5>&1;
  output=$(docker pull ptpuscas/mobile_rt:"${1}" | tee /dev/fd/5 || true);
  echo "Docker: ${output}";
  if [[ ${output} != *"up to date"* && ${output} != *"Downloaded newer image for"* ]]; then
    echo "Did not find the Docker image. Will have to build the image.";
    export BUILT_IMAGE="yes";
  else
    echo "Docker image found!";
    export BUILT_IMAGE="no";
  fi
}

# Helper command to update and compile the MobileRT in a docker container.
# It builds the MobileRT in release mode.
# The parameters are:
# * VERSION
function compileMobileRTInDockerContainer() {
  docker run -t \
    --entrypoint bash \
    --name="mobile_rt_${1}" \
    --volume="${PWD}":/MobileRT_volume \
    ptpuscas/mobile_rt:"${1}" \
    -c "cd ../ \
      && cp -rpf ../MobileRT_volume/* ./ \
      && find ./app/third_party/ -mindepth 1 -maxdepth 1 -type d ! -regex '^./app/third_party\(/conan*\)?' -exec rm -rf {} \; \
      && ls -lahp ./ \
      && chmod -R +x ./scripts/ \
      && ls -lahp ./scripts/ \
      && bash ./scripts/install_dependencies.sh \
      && bash ./scripts/compile_native.sh -t release -c g++ -r yes";
}

# Helper command to execute the MobileRT unit tests in the docker container.
# The parameters are:
# * VERSION
function executeUnitTestsInDockerContainer() {
  docker run -t \
    --entrypoint bash \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    -e DISPLAY="${DISPLAY}" \
    --name="mobile_rt_${1}" \
    ptpuscas/mobile_rt:"${1}" -c "./bin/UnitTests";
}

# Helper command to push the MobileRT docker image into the docker registry.
# The parameters are:
# * VERSION
function pushMobileRTDockerImage() {
  docker push ptpuscas/mobile_rt:"${1}";
}

# Helper command to commit a layer into the MobileRT docker image.
# The parameters are:
# * VERSION
function commitMobileRTDockerImage() {
  docker commit mobile_rt_"${1}" ptpuscas/mobile_rt:"${1}";
  docker rm mobile_rt_"${1}";
}

# Helper command to squash all the layers of the MobileRT docker image.
# The parameters are:
# * VERSION
function squashMobileRTDockerImage() {
  _installDockerSquashCommand;
  echo "docker history 1";
  docker history ptpuscas/mobile_rt:"${1}" || true;
  echo "docker history 2";
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" || true;
  echo "docker history 3";
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" || true;
  echo "docker history 4";
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" | head -2 || true;
  echo "docker history 5";
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" | head -2 | tail -1 || true;
  local LAST_LAYER_ID;
  LAST_LAYER_ID=$(docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" | head -2 | tail -1 | cut -d ' ' -f 1 || true);
  echo "LAST_LAYER_ID=${LAST_LAYER_ID}";
  docker-squash -v --tag ptpuscas/mobile_rt:"${1}" ptpuscas/mobile_rt:"${1}";
  echo "docker squash finished";
  docker history ptpuscas/mobile_rt:"${1}" || true;
}

# Helper command to install the docker-squash command.
function _installDockerSquashCommand() {
  pip install --upgrade pip --user;
  pip3 install --upgrade pip --user;
  executeWithoutExiting python -m pip install --upgrade pip --user;
  executeWithoutExiting python3 -m pip install --upgrade pip --user;

  pip install -v docker==5.0.3;
  pip install -v docker-squash;

  pip list -v | grep -i docker;
  pip list -v --outdated | grep -i docker;
  pip show -v docker docker-squash;
  pip freeze -v | grep -i docker;
}

# Helper command to install the docker command for MacOS.
# This code installs Docker on MacOS but the command `docker` doesn't do anything.
# Its necessary to install Docker Desktop on Mac:
# https://docs.docker.com/docker-for-mac/install/
# So, for now, we just use MacOS docker image that uses KVM (Kernel-based Virtual Machine)
# in a Linux environment.
function installDockerCommandForMacOS() {
  echo "Select XCode.";
  sudo xcode-select --switch /System/Volumes/Data/Applications/Xcode.app/Contents/Developer;
  echo "Update Homebrew";
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)";
  echo "Install docker and virtualbox";
  brew install --cask docker virtualbox;
  brew install docker;
  echo "Install docker-machine";
  mkdir -p ~/.docker/machine/cache/;
  curl -Lo ~/.docker/machine/cache/boot2docker.iso https://github.com/boot2docker/boot2docker/releases/download/v19.03.12/boot2docker.iso;
  brew install docker-machine;
  echo "Create docker-machine";
  docker-machine create --driver virtualbox --virtualbox-boot2docker-url ~/.docker/machine/cache/boot2docker.iso default;
  echo "Start service docker-machine";
  brew services start docker-machine;
  eval "$(docker-machine env default)";
  echo "Restart service docker-machine";
  docker-machine restart;
  docker-machine env;
  docker ps;
  docker --version;

  # shellcheck disable=SC2086,SC2010
  ls ${PATH//:/ } 2> /dev/null | grep -i docker 2> /dev/null || true;
  export PATH=${PATH}:"/usr/local/bin/";

  echo "Start Docker";
  git clone https://github.com/docker/docker.github.io.git;
  pushd docker.github.io || exit;
  ls registry/recipes/osx;
  plutil -lint registry/recipes/osx/com.docker.registry.plist;
  cp registry/recipes/osx/com.docker.registry.plist ~/Library/LaunchAgents/;
  chmod 644 ~/Library/LaunchAgents/com.docker.registry.plist;
  launchctl load ~/Library/LaunchAgents/com.docker.registry.plist;
  echo "Restart Docker registry";
  launchctl stop com.docker.registry;
  launchctl start com.docker.registry;
  launchctl unload ~/Library/LaunchAgents/com.docker.registry.plist;

  open /Applications/Docker.app;
  popd || exit;
}
