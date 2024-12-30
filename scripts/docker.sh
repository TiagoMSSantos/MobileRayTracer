#!/usr/bin/env sh

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
checkAvailableVersion() {
  echo "${PATH}" | sed 's/:/ /g' | xargs ls 2> /dev/null | grep -i docker 2> /dev/null || true;
  docker --version;
}

# Helper command to build the MobileRT docker image.
# It builds the image in release mode.
# The parameters are:
# * BASE_IMAGE
# * BRANCH
# * VERSION
buildDockerImage() {
  prepareBinaries .;
  du -h -d 1 scripts;

  if echo "${1}" | grep -iq 'microsoft' || echo "${1}" | grep -iq 'windows'; then
    echo 'Building Docker image based on Windows.';
    dockerBaseOS='windows';
    echo 'Adding bin to PATH and expecting the BuildKit is installed there.';
    export PATH="${PATH}:bin";
    export DOCKER_BUILDKIT=1;
    docker build \
      -f deploy/Dockerfile."${dockerBaseOS}" \
      --no-cache=false \
      --rm=true \
      --build-arg BUILDKIT_INLINE_CACHE=1 \
      --build-arg BASE_IMAGE="${1}" \
      --build-arg BRANCH="${2}" \
      --build-arg BUILD_TYPE=release \
      .;
  else
    echo 'Building Docker image based on Unix.';
    dockerBaseOS='unix';
    export DOCKER_BUILDKIT=1;
    docker build \
      -f deploy/Dockerfile."${dockerBaseOS}" \
      --no-cache=false \
      --rm=true \
      --build-arg BUILDKIT_INLINE_CACHE=1 \
      --build-arg BASE_IMAGE="${1}" \
      --build-arg BRANCH="${2}" \
      --build-arg BUILD_TYPE=release \
      .;
  fi

  dockerImageBuilt=$?;
  if [ "${dockerImageBuilt}" != "0" ]; then
    echo 'Failed to build image.';
    exit 1;
  fi

  imageId=$(docker images | awk '{print $3}' | awk 'NR==2');
  echo "MobileRT imageId: ${imageId}";
  docker tag "${imageId}" ptpuscas/mobile_rt:"${3}";
  docker images;
}

# Helper command to pull the MobileRT docker image.
# The parameters are:
# * IMAGE
pullDockerImage() {
  export DOCKER_BUILDKIT=1;
  rm -f /tmp/fd3;
  exec 3<> /tmp/fd3; # Open file descriptor 3.
  i=1;
  parallelizeBuild;
  echo "Creating '${NCPU_CORES}' processes to perform docker pull.";
  while [ "${i}" -le "${NCPU_CORES}" ]; do
    echo "Scheduling docker pull process: ${i}";
    docker pull "${1}" | tee /tmp/fd3 &
    i=$((i + 1));
  done
  echo 'jobs created:';
  jobs -p;
  # shellcheck disable=SC2046
  wait $(jobs -p);
  output=$(cat /tmp/fd3);
  echo "Docker: ${output}";
  if echo "${output}" | grep -q 'up to date' || echo "${output}" | grep -q 'Downloaded newer image for'; then
    echo 'Docker image found!';
  else
    echo 'Did not find the Docker image. Will have to build the image.';
  fi
  exec 3>&-; # Close file descriptor 3.
}

# Helper command to update and compile the MobileRT in a docker container.
# It builds the MobileRT in release mode.
# The parameters are:
# * VERSION
compileMobileRTInDockerContainer() {
  export DOCKER_BUILDKIT=1;
  if echo "${1}" | grep -iq 'microsoft' || echo "${1}" | grep -iq 'windows'; then
    echo 'Compiling MobileRT in Windows based Docker image.';
    currentPath=$(echo "${PWD}" | sed 's/\\/\//g' | sed 's/\/d\//D:\//' );
    mobilertVolumeInContainer='C:/MobileRT/MobileRT_volume';
  else
    echo 'Compiling MobileRT in Unix based Docker image.';
    currentPath="${PWD}";
    mobilertVolumeInContainer='/MobileRT_volume';
  fi
  echo "Current path: ${currentPath}";
  echo "Volume path: ${mobilertVolumeInContainer}";
  echo "Current commit: $(git log -1 --format=%H-%B)";
  docker run -t \
    --name="mobile_rt_built_${1}" \
    --volume="${currentPath}":"${mobilertVolumeInContainer}" \
    ptpuscas/mobile_rt:"${1}" \
      "echo Current path in container: $(pwd) \
      && cp -rpf ${mobilertVolumeInContainer}/* . \
      && rm -rf app/third_party/boost \
      && rm -rf app/third_party/glm \
      && rm -rf app/third_party/googletest \
      && rm -rf app/third_party/pcg-cpp \
      && rm -rf app/third_party/stb \
      && rm -rf app/third_party/tinyobjloader \
      && rm -rf build_* \
      && git init \
      && ls -lahp . \
      && sh scripts/compile_native.sh -t release -c g++ -r yes \
      && rm -rf app/third_party/boost \
      && rm -rf app/third_party/glm \
      && rm -rf app/third_party/googletest \
      && rm -rf app/third_party/pcg-cpp \
      && rm -rf app/third_party/stb \
      && rm -rf app/third_party/tinyobjloader \
      && rm -rf app/System_dependent/Android_JNI \
      && rm -rf app/release \
      && rm -rf app/src/main \
      && rm -rf app/src/test \
      && rm -rf app/src/androidTest/java \
      && rm -rf app/src/androidTest/*.xml \
      && rm -rf app/src/androidTest/resources/APKs \
      && rm -rf app/src/androidTest/resources/Shaders \
      && rm -rf app/src/androidTest/resources/teapot \
      && rm -rf app/*.classpath \
      && rm -rf app/*.project \
      && rm -rf app/*.gradle \
      && rm -rf app/*.xml \
      && rm -rf app/*.jks \
      && rm -rf app/*.pro \
      && rm -rf .git \
      && rm -rf .github \
      && rm -rf docs \
      && rm -rf gradle \
      && rm -rf *.yml \
      && rm -rf *.codedocs \
      && rm -rf *.dockerignore \
      && rm -rf *.json \
      && rm -rf *.project \
      && rm -rf *.simplecov \
      && rm -rf *.gradle \
      && rm -rf Gemfile* \
      && rm -rf *.properties \
      && rm -rf gradlew* \
      && rm -rf Makefile* \
      && rm -rf **/*.wh.* \
      ";
}

# Helper command to execute the MobileRT unit tests in the docker container.
# The parameters are:
# * VERSION of the docker image
# * PARAMETERS for the unit tests
executeUnitTestsInDockerContainer() {
  # export DOCKER_BUILDKIT=1;
  docker run -t \
    -e DISPLAY="${DISPLAY}" \
    --name="mobile_rt_tests_${1}" \
    ptpuscas/mobile_rt:"${1}" "set";
  docker rm --force --volumes "mobile_rt_tests_${1}";
}

# Helper command to push the MobileRT docker image into the docker registry.
# The parameters are:
# * VERSION
pushMobileRTDockerImage() {
  export DOCKER_BUILDKIT=1;
  docker push ptpuscas/mobile_rt:"${1}";
}

# Helper command to commit a layer into the MobileRT docker image.
# The parameters are:
# * VERSION
commitMobileRTDockerImage() {
  export DOCKER_BUILDKIT=1;
  docker commit mobile_rt_built_"${1}" ptpuscas/mobile_rt:"${1}";
  docker rm mobile_rt_built_"${1}";
}

# Helper command to squash all the layers of the MobileRT docker image.
# The parameters are:
# * VERSION
squashMobileRTDockerImage() {
  export DOCKER_BUILDKIT=1;
  _installDockerSquashCommand;
  echo 'docker history 1';
  docker history ptpuscas/mobile_rt:"${1}" || true;
  echo 'docker history 2';
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" || true;
  echo 'docker history 3';
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" || true;
  echo 'docker history 4';
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" | head -2 || true;
  echo 'docker history 5';
  docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" | head -2 | tail -1 || true;
  LAST_LAYER_ID=$(docker history ptpuscas/mobile_rt:"${1}" | grep -v "<missing>" | head -2 | tail -1 | tr -s ' ' | cut -d ' ' -f 1 || true);
  echo "LAST_LAYER_ID=${LAST_LAYER_ID}";
  docker-squash -v --tag ptpuscas/mobile_rt:"${1}" ptpuscas/mobile_rt:"${1}";
  echo 'docker squash finished';
  docker history ptpuscas/mobile_rt:"${1}" || true;
}

# Helper command to install the docker-squash command.
_installDockerSquashCommand() {
  pip install --upgrade pip --user;
  pip3 install --upgrade pip --user;
  python -m pip install --upgrade pip --user || true;
  python3 -m pip install --upgrade pip --user || true;

  pip install -v docker==5.0.3;
  pip install -v docker-squash;

  pip list -v | grep -i docker;
  pip list -v --outdated | grep -i docker;
  pip show -v docker docker-squash;
  pip freeze -v | grep -i docker;
}

# Helper function to install the docker command for MacOS.
# Its necessary to install Docker Desktop on Mac:
# https://docs.docker.com/docker-for-mac/install/
installDockerCommandForMacOS() {
  MAJOR_MAC_VERSION=$(sw_vers | grep ProductVersion | cut -d ':' -f2 | cut -d '.' -f1 | tr -d '[:space:]');
  echo "MacOS '${MAJOR_MAC_VERSION}' detected";

  # brew update;
  echo 'Avoid homebrew from auto-update itself every time its installed something.';
  export HOMEBREW_NO_AUTO_UPDATE=1;

  if [ "${MAJOR_MAC_VERSION}" = 11 ]; then
    echo 'Updating MacPorts.';
    sudo port -bn selfupdate;
    echo 'Installing docker via MacPorts.';
    sudo port -bn install docker;
    echo "Ignoring errors from homebrew, because MacOS '${MAJOR_MAC_VERSION}' was detected.";
    set +e;
  fi
  echo 'Install docker & colima.';
  brew list docker > /dev/null 2>&1 || brew install --skip-cask-deps homebrew/cask/docker;
  brew list colima > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install colima;
  brew list lima > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install lima;
  # echo 'Install qemu.';
  # brew list qemu > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install qemu;
  # brew list libssh > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install libssh;
  # brew list libslirp > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install libslirp;
  # brew list capstone > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install capstone;
  # brew list dtc > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install dtc;
  # brew list snappy > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install snappy;
  # brew list vde > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install vde;
  # brew list ncurses > /dev/null 2>&1 || brew install --skip-cask-deps --skip-post-install ncurses;
  set -e;

  if [ "${MAJOR_MAC_VERSION}" -gt 13 ]; then
    echo 'Installing a specific version of colima.';
    rm -rf ~/.colima;
    rm -rf ~/.lima;
    rm -rf ~/Library/Caches/colima;
    rm -rf ~/Library/Caches/lima;
    # Use version v0.6.8 to fix hanging at level=info msg="Expanding to 14GiB". More info: https://github.com/abiosoft/colima/issues/930
    curl -o /tmp/colima.rb -L https://github.com/abiosoft/colima/releases/download/v0.6.8/colima-Darwin-x86_64;
    chmod +x /tmp/colima.rb;
  else
    ln -s "$(which colima)" /tmp/colima.rb;
  fi

  echo 'Start colima.';
  /tmp/colima.rb start --help;
  parallelizeBuild;
  memSizeBytes=$(sysctl -n hw.memsize);
  memSizeGB=$((memSizeBytes / (1024 * 1024 * 1024)));
  echo "System Memory: ${memSizeGB} GB";
  df -h;
  set +e;
  # Check available resources: https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners/about-github-hosted-runners#standard-github-hosted-runners-for-public-repositories
  # To setup Colima for MacOS with CPU ARM M1: https://www.tyler-wright.com/using-colima-on-an-m1-m2-mac
  /tmp/colima.rb start --cpu "${NCPU_CORES}" --memory ${memSizeGB} --disk 14 --mount-type=virtiofs;
  startedColima=$?;
  set -e;
  if [ "${startedColima}" != "0" ]; then
    echo 'Starting colima failed. Printing the error logs.';
    cat /Users/runner/.colima/_lima/colima/ha.stderr.log;
    exit 1;
  fi

  echo 'Docker commands detected:';
  echo "${PATH}" | sed 's/:/ /g' | xargs ls 2> /dev/null | grep -i docker 2> /dev/null || true;

  echo 'Validating docker command works.';
  docker --version;
  docker info;
  docker image ls -a;
  docker ps -a;
}
