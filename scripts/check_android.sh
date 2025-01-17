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
if [ $# -ge 1 ] && command -v shellcheck > /dev/null; then
  shellcheck "${0}" || return 1;
fi
###############################################################################
###############################################################################


###############################################################################
# Set default arguments.
###############################################################################
android_api_version='14';
cpu_architecture='"x86","x86_64"';
parallelizeBuild;

printEnvironment() {
  echo '';
  echo 'Selected arguments:';
  echo "android_api_version: ${android_api_version}";
  echo "cpu_architecture: ${cpu_architecture}";
}
###############################################################################
###############################################################################


###############################################################################
# Set paths.
###############################################################################
echo 'Set path to linter reports';
reports_path='app/build/reports';
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
  sh gradlew --offline --parallel \
    -DandroidApiVersion="${android_api_version}" -DabiFilters="[${cpu_architecture}]" \
    --no-rebuild --stop --info --warning-mode fail --stacktrace;
  sh gradlew --offline --parallel \
    -DandroidApiVersion="${android_api_version}" -DabiFilters="[${cpu_architecture}]" \
    --no-rebuild --version --info --warning-mode fail --stacktrace;

  echo 'Calling the Gradle linter';
  sh gradlew lint --profile --parallel \
    -DandroidApiVersion="${android_api_version}" -DabiFilters="[${cpu_architecture}]" \
    --no-rebuild \
    --console plain --info --warning-mode all --stacktrace;
  resCheck=${?};
}
###############################################################################
###############################################################################

# Increase memory for heap.
export GRADLE_OPTS='-Xms4G -Xmx4G -XX:ActiveProcessorCount=4';
createReportsFolders;
runLinter;

linterReportPath="${PWD}/${reports_path}";
linterReport='lint-results-debug.html';
checkPathExists "${linterReportPath}" "${linterReport}";
printf '\n\e]8;;file://'"%s"'\aClick here to check the Linter report.\e]8;;\a\n' "${linterReportPath}/${linterReport}";

###############################################################################
# Exit code
###############################################################################
printCommandExitCode "${resCheck}" 'Check';
###############################################################################
###############################################################################
