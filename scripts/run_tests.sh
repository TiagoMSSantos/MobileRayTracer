#!/usr/bin/env sh

###############################################################################
# README
###############################################################################
# This script runs the Unit Tests of MobileRT in the native Operating System.
###############################################################################
###############################################################################


###############################################################################
# Exit immediately if a command exits with a non-zero status.
###############################################################################
set -eu;
###############################################################################
###############################################################################


###############################################################################
# Change directory to MobileRT root.
###############################################################################
if [ $# -ge 1 ]; then
  cd "$(dirname "${0}")/.." || return 1;
fi
###############################################################################
###############################################################################


###############################################################################
# Get helper functions.
###############################################################################
# shellcheck disable=SC1091
. scripts/helper_functions.sh;
###############################################################################
###############################################################################


###############################################################################
# Execute Shellcheck on this script.
###############################################################################
if [ $# -ge 1 ] && command -v shellcheck > /dev/null; then
  shellcheck "${0}" || return 1;
fi
###############################################################################
###############################################################################


###############################################################################
# Set default arguments.
###############################################################################
type='release';
ndk_version='23.2.8568313';
cmake_version='3.22.1';
gradle_version='8.2.2';
cpu_architecture='"x86","x86_64"';
parallelizeBuild;

printEnvironment() {
  echo '';
  echo 'Selected arguments:';
  echo "type: ${type}";
  echo "ndk_version: ${ndk_version}";
  echo "cmake_version: ${cmake_version}";
  echo "gradle_version: ${gradle_version}";
  echo "cpu_architecture: ${cpu_architecture}";
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
reports_path=app/build/reports;
mkdir -p ${reports_path};

type=$(capitalizeFirstletter "${type}");
echo "type: '${type}'";

runUnitTests() {
  echo 'Calling Gradle test';
  echo 'Increasing ADB timeout to 10 minutes';
  export ADB_INSTALL_TIMEOUT=60000;
  sh gradlew --no-rebuild --stop --info --warning-mode fail --stacktrace;
  sh gradlew test"${type}"UnitTest --profile --parallel \
    -DndkVersion="${ndk_version}" -DcmakeVersion="${cmake_version}" -DgradleVersion="${gradle_version}" \
    -DabiFilters="[${cpu_architecture}]" \
    --no-rebuild \
    --console plain --info --warning-mode all --stacktrace;
  resUnitTests=${?};
}
###############################################################################
###############################################################################

# Increase memory for heap.
export GRADLE_OPTS="-Xms4G -Xmx4G -XX:ActiveProcessorCount=3";
createReportsFolders;
runUnitTests;

unitTestsReport="${PWD}/${reports_path}/tests/test${type}UnitTest/index.html";
validateFileExistsAndHasSomeContent "${unitTestsReport}";

echo '';
printf '\e]8;;file://'"%s"'\aClick here to check the Unit tests report.\e]8;;\a\n' "${unitTestsReport}";
echo '';
echo '';

###############################################################################
# Exit code.
###############################################################################
printCommandExitCode "${resUnitTests}" 'Unit tests';
###############################################################################
###############################################################################
