#!/bin/bash

###############################################################################
# README
###############################################################################
# This script runs the Unit Tests of MobileRT in the native Operating System.
###############################################################################
###############################################################################


###############################################################################
# Exit immediately if a command exits with a non-zero status.
###############################################################################
set -euo pipefail;
###############################################################################
###############################################################################


###############################################################################
# Change directory to MobileRT root.
###############################################################################
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit;
###############################################################################
###############################################################################


###############################################################################
# Get helper functions.
###############################################################################
# shellcheck disable=SC1091
source scripts/helper_functions.sh;
###############################################################################
###############################################################################


###############################################################################
# Execute Shellcheck on this script.
###############################################################################
if [ -x "$(command -v shellcheck)" ]; then
  shellcheck "${0}" || exit
fi
###############################################################################
###############################################################################


###############################################################################
# Set default arguments.
###############################################################################
type="release";
ndk_version="21.3.6528147";
cmake_version="3.10.2";
parallelizeBuild;

function printEnvironment() {
  echo "";
  echo "Selected arguments:";
  echo "type: ${type}";
  echo "ndk_version: ${ndk_version}";
  echo "cmake_version: ${cmake_version}";
}
###############################################################################
###############################################################################


###############################################################################
# Parse arguments.
###############################################################################
parseArgumentsToCompileAndroid "$@";
printEnvironment;
###############################################################################
###############################################################################


###############################################################################
# Run unit tests natively.
###############################################################################

# Set path to reports.
reports_path=./app/build/reports;
mkdir -p ${reports_path};

type=$(capitalizeFirstletter "${type}");
echo "type: '${type}'";

function runUnitTests() {
  echo "Calling Gradle test";
  echo "Increasing ADB timeout to 10 minutes";
  export ADB_INSTALL_TIMEOUT=60000;
  ./gradlew --stop;
  ./gradlew test"${type}"UnitTest --profile --parallel \
    -DndkVersion="${ndk_version}" -DcmakeVersion="${cmake_version}" \
    --console plain \
    2>&1 | tee ${reports_path}/log_native_tests_"${type}".log;
  resUnitTests=${PIPESTATUS[0]};
}
###############################################################################
###############################################################################

runUnitTests;
echo "";
echo -e '\e]8;;file:///'"${PWD}"'/'${reports_path}'/tests/test'"${type}"'UnitTest/index.html\aClick here to check the Unit tests report.\e]8;;\a';
echo "";
echo "";

###############################################################################
# Exit code.
###############################################################################
printCommandExitCode "${resUnitTests}" "Unit tests";
###############################################################################
###############################################################################
