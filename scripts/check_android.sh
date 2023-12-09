#!/usr/bin/env sh

###############################################################################
# README
###############################################################################
# This script runs the gradle linter in the codebase.
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
if command -v shellcheck > /dev/null; then
  shellcheck "${0}" || return 1;
fi
###############################################################################
###############################################################################


###############################################################################
# Set default arguments.
###############################################################################
ndk_version='23.2.8568313';
cmake_version='3.22.1';
cpu_architecture='"x86","x86_64"';
parallelizeBuild;

printEnvironment() {
  echo '';
  echo 'Selected arguments:';
  echo "ndk_version: ${ndk_version}";
  echo "cmake_version: ${cmake_version}";
  echo "cpu_architecture: ${cpu_architecture}";
}
###############################################################################
###############################################################################


###############################################################################
# Parse arguments.
###############################################################################
parseArgumentsToCheck "$@";
printEnvironment;
###############################################################################
###############################################################################


###############################################################################
# Run Gradle linter.
###############################################################################
runLinter() {
  # Set path to reports.
  echo 'Print Gradle version';
  sh gradlew --no-rebuild --stop --info --warning-mode fail --stacktrace;
  sh gradlew --no-rebuild --version --info --warning-mode fail --stacktrace;

  echo 'Calling the Gradle linter';
  sh gradlew lint --profile --parallel \
    -DndkVersion="${ndk_version}" -DcmakeVersion="${cmake_version}" \
    -DabiFilters="[${cpu_architecture}]" \
    --no-rebuild \
    --console plain --info --warning-mode all --stacktrace;
  resCheck=${?};
}
###############################################################################
###############################################################################

# Increase memory for heap.
export GRADLE_OPTS="-Xms4G -Xmx4G -XX:ActiveProcessorCount=3";
createReportsFolders;
runLinter;

###############################################################################
# Exit code
###############################################################################
printCommandExitCode "${resCheck}" 'Check';
###############################################################################
###############################################################################
