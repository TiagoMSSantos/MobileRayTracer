#!/bin/bash

###############################################################################
# Execute Shellcheck on this script.
###############################################################################
if [ -x "$(command -v shellcheck)" ]; then
  shellcheck "${0}";
fi
###############################################################################
###############################################################################

###############################################################################
# README
###############################################################################
# This script contains a bunch of helper functions for the bash scripts.
###############################################################################
###############################################################################

# Helper command for compilation scripts.
function helpCompile() {
  echo "Usage: cmd [-h] [-t type] [-c compiler] [-r recompile]";
  exit 0;
}

# Helper command for Android compilation scripts.
function helpCompileAndroid() {
  echo "Usage: cmd [-h] [-t type] [-c compiler] [-r recompile] [-n ndk_version] [-m cmake_version]";
  exit 0;
}

# Helper command for Android run tests scripts.
function helpTestAndroid() {
  echo "Usage: cmd [-h] [-t type] [-r run_test] [-n ndk_version] [-m cmake_version] [-k kill_previous]";
  exit 0;
}

# Helper command for compilation scripts.
function helpCheck() {
  echo "Usage: cmd [-h] [-n ndk_version] [-m cmake_version]";
  exit 0;
}

# Argument parser for compilation scripts.
function parseArgumentsToCompile() {
  while getopts ":ht:c:r:" opt; do
    case ${opt} in
      t )
        export type=${OPTARG};
        ;;
      c )
        export compiler=${OPTARG};
        checkCommand "${compiler}";
        ;;
      r )
        export recompile=${OPTARG};
        ;;
      h )
        helpCompile;
        ;;
      \? )
        helpCompile;
        ;;
    esac
  done
}

# Argument parser for Android compilation scripts.
function parseArgumentsToCompileAndroid() {
  while getopts ":ht:c:r:n:m:" opt; do
    case ${opt} in
      n )
        export ndk_version=${OPTARG};
        ;;
      m )
        export cmake_version=${OPTARG};
        ;;
      t )
        export type=${OPTARG};
        ;;
      c )
        export compiler=${OPTARG};
        checkCommand "${compiler}";
        ;;
      r )
        export recompile=${OPTARG};
        ;;
      h )
        helpCompileAndroid;
        ;;
      \? )
        helpCompileAndroid;
        ;;
    esac
  done
}

# Argument parser for Android run tests scripts.
function parseArgumentsToTestAndroid() {
  while getopts ":ht:r:k:n:m:" opt; do
    case ${opt} in
      n )
        export ndk_version=${OPTARG};
        ;;
      m )
        export cmake_version=${OPTARG};
        ;;
      t )
        export type=${OPTARG};
        ;;
      r )
        export run_test=${OPTARG};
        ;;
      k )
        export kill_previous=${OPTARG};
        ;;
      h )
        helpTestAndroid;
        ;;
      \? )
        helpTestAndroid;
        ;;
    esac
  done
}

# Argument parser for linter scripts.
function parseArgumentsToCheck() {
  while getopts ":hm:n:" opt; do
    case ${opt} in
      n )
        export ndk_version=${OPTARG};
        ;;
      m )
        export cmake_version=${OPTARG};
        ;;
      h )
        helpCheck;
        ;;
      \? )
        helpCheck;
        ;;
    esac
  done
}

# Call function multiple times until it fails and exit the process.
function callCommandUntilError() {
  echo "";
  echo "Calling until error '$*'";
  local retry=0;
  "$@";
  local lastResult=${PIPESTATUS[0]};
  while [[ "${lastResult}" -eq 0 && retry -lt 5 ]]; do
    retry=$(( retry + 1 ));
    "$@";
    lastResult=${PIPESTATUS[0]};
    echo "Retry: ${retry} of command '$*'; result: '${lastResult}'";
    sleep 5;
  done
  if [ "${lastResult}" -eq 0 ]; then
    echo "$*: success - '${lastResult}'";
  else
    echo "$*: failed - '${lastResult}'";
    echo "";
    exit "${lastResult}";
  fi
}

# Call function multiple times until it doesn't fail and then return.
function callCommandUntilSuccess() {
  echo "";
  echo "Calling until success '$*'";
  local retry=0;
  set +e;
  "$@";
  local lastResult=${PIPESTATUS[0]};
  echo "result: '${lastResult}'";
  while [[ "${lastResult}" -ne 0 && retry -lt 20 ]]; do
    retry=$(( retry + 1 ));
    "$@";
    lastResult=${PIPESTATUS[0]};
    echo "Retry: ${retry} of command '$*'; result: '${lastResult}'";
    sleep 3;
  done
  set -e;
  if [ "${lastResult}" -eq 0 ]; then
    echo "'$*': success";
  else
    echo "'$*': failed";
    exit "${lastResult}";
  fi
}

# Call an ADB shell function multiple times until it doesn't fail and then return.
function callAdbShellCommandUntilSuccess() {
  echo "";
  echo "Calling ADB shell command until success '$*'";
  local retry=0;
  local output;
  output=$("$@");
  # echo "Output of command: '${output}'";
  local lastResult;
  lastResult=$(echo "${output}" | grep '::.*::' | sed 's/:://g'| tr -d '[:space:]');
  echo "result: '${lastResult}'";
  while [[ "${lastResult}" -ne 0 && retry -lt 10 ]]; do
    retry=$(( retry + 1 ));
    output=$("$@");
    echo "Output of command: '${output}'";
    lastResult=$(echo "${output}" | grep '::.*::' | sed 's/:://g' | tr -d '[:space:]');
    echo "Retry: ${retry} of command '$*'; result: '${lastResult}'";
    sleep 3;
  done
  if [ "${lastResult}" -eq 0 ]; then
    echo "'$*': success";
  else
    echo "'$*': failed";
    exit "${lastResult}";
  fi
}

# Outputs the exit code received by argument and exits the current process with
# that exit code.
function printCommandExitCode() {
  echo "######################################################################";
  echo "Results:";
  if [ "${1}" -eq 0 ]; then
    echo "${2}: success";
  else
    echo "${2}: failed";
    exit "${1}";
  fi
}

# Check command is available.
function checkCommand() {
  if [ -x "$(command -v "${@}")" ]; then
    echo "Command '$*' installed!";
  else
    echo "Command '$*' is NOT installed.";
    if [[ $(uname -a) == *"MINGW"* ]]; then
      echo "Detected Windows OS, so ignoring this error ...";
      exit 0;
    fi
    exit 1;
  fi
}

# Capitalize 1st letter.
function capitalizeFirstletter() {
  local res;
  res="$(tr '[:lower:]' '[:upper:]' <<<"${1:0:1}")${1:1}";
  echo "${res}";
}

# Parallelize building of MobileRT.
function parallelizeBuild() {
  if [ -x "$(command -v nproc)" ]; then
    MAKEFLAGS=-j$(nproc --all);
  else
    # Assuming MacOS.
    MAKEFLAGS=-j$(sysctl -n hw.logicalcpu);
  fi
  export MAKEFLAGS;
}

# Check the files that were modified in the last few minutes.
function checkLastModifiedFiles() {
  local MINUTES;
  MINUTES=50;
  set +e;
  echo "#####################################################################";
  echo "Files modified in home:";
  find ~/ -type f -mmin -${MINUTES} -print 2> /dev/null | grep -v "mozilla" | grep -v "thunderbird" | grep -v "java";
  echo "#####################################################################";
  echo "Files modified in CI runner:";
  find /home/runner -type f -mmin -${MINUTES} -print 2> /dev/null;
  echo "#####################################################################";
  set -e;
}

# Check the report's paths.
function checkReportsPaths() {
  ls -lah ./app;
  ls -lah ./app/build;
  ls -lah ./app/build/reports;
  ls -lah ./app/build/reports/coverage;
  ls -lah ./app/build/reports/coverage/androidTest/debug/report.xml;
  ls -lah ./build/reports;
  find app/build/reports -iname "*xml*";
}

# Change the mode of all binaries/scripts to be able to be executed.
function prepareBinaries() {
  chmod -R +x scripts/;
  chmod +x ./test-reporter-latest-linux-amd64;
  chmod +x ./test-reporter-latest-darwin-amd64;
}

# Helper command to execute a command / function without exiting the script (without the set -e).
function executeWithoutExiting () {
  set +e;
  "$@";
  set -e;
}

# Private method which kills a process that is using a file.
function _killProcessUsingFile() {
  local processes_using_file;
  processes_using_file=$(lsof "${1}" | tail -n +2 | tr -s ' ');
  local retry=0;
  while [[ "${processes_using_file}" != "" && retry -lt 5 ]]; do
    retry=$(( retry + 1 ));
    echo "processes_using_file: '${processes_using_file}'";
    local process_id_using_file;
    process_id_using_file=$(echo "${processes_using_file}" | cut -d ' ' -f 2 | head -1);
    echo "Going to kill this process: '${process_id_using_file}'";
    callCommandUntilSuccess kill -SIGKILL "${process_id_using_file}";
    set +e;
    processes_using_file=$(lsof "${1}" | tail -n +2 | tr -s ' ');
    set -e;
  done
}

# Delete all old build files (commonly called ".fuse_hidden<id>") that might not be able to be
# deleted due to some process still using it. So this method detects which process uses them and
# kills it first.
function clearOldBuildFiles() {
  files_being_used=$(find . -iname "*.fuse_hidden*" || true);
  local retry=0;
  while [[ "${files_being_used}" != "" && retry -lt 5 ]]; do
    retry=$(( retry + 1 ));
    echo "files_being_used: '${files_being_used}'";
    while IFS= read -r file; do
      while [[ -f "${file}" ]]; do
        _killProcessUsingFile "${file}";
        echo "sleeping 2 secs";
        sleep 2;
        set +e;
        rm "${file}";
        set -e;
      done
    done <<<"${files_being_used}";
    files_being_used=$(find . -iname "*.fuse_hidden*" | grep -i ".fuse_hidden" || true);
  done
}

# Create the reports' folders.
function createReportsFolders() {
  echo "Creating reports folders.";
  mkdir -p ./build/reports;
  mkdir -p ./app/build/reports;
  echo "Created reports folders.";
}

# Validate MobileRT native lib was compiled.
function validateNativeLibCompiled() {
  local nativeLib;
  nativeLib=$(find . -iname "*mobilert*.so");
  find . -iname "*.so" 2> /dev/null;
  echo "nativeLib: ${nativeLib}";
  if [ "$(echo "${nativeLib}" | wc -l)" -eq 0 ]; then
    exit 1;
  fi
}

# Extract and check files from downloaded artifact.
function extractFilesFromSonarArtifact() {
  ls -lah ~/;
  ls -lah ~/.sonar;
  unzip -o ~/.sonar/sonar-packages.zip -d ~/.sonar/;
  rm ~/.sonar/sonar-packages.zip;
  ls -lah ~/.sonar;
  ls -lah ~/.gradle;
  du -h --time --max-depth=1 ~/;
}

# Create symlinks of headers for MacOS.
function createSymlinksOfQtForMacOS() {
  find /usr/local -iname "Find*Qt*.cmake" 2> /dev/null;
  echo "Checking available versions of Xcode.";
  ls -lah /System/Volumes/Data/Applications;

  echo "Define Qt versions.";
  ls -lah /usr/local/Cellar/;
  export QT_4_VERSION;
  QT_4_VERSION="$(ls /usr/local/Cellar/qt@4/ | head -1)";
  # export QT_5_VERSION="$(ls /usr/local/Cellar/qt@5/ | head -1)";

  echo "Check Qt 5 paths exist.";
  # Qt5 might fail while installing via homebrew.
  # ls -lah /usr/local/Cellar/qt@5/;
  # ls -lah /usr/local/Cellar/qt@5/${QT_5_VERSION}/lib/;
  # ls -lah /usr/local/Cellar/qt@5/${QT_5_VERSION}/lib/QtCore.framework/Versions/5/Headers/;
  # ls -lah /usr/local/Cellar/qt@5/${QT_5_VERSION}/lib/QtGui.framework/Versions/5/Headers/;
  # ls -lah /usr/local/Cellar/qt@5/${QT_5_VERSION}/lib/QtWidgets.framework/Versions/5/Headers/

  echo "Check Qt 4 paths exist.";
  ls -lah /usr/local/Cellar/qt@4/;
  ls -lah /usr/local/Cellar/qt@4/"${QT_4_VERSION}"/lib/;
  ls -lah /usr/local/Cellar/qt@4/"${QT_4_VERSION}"/lib/QtCore.framework/Versions/4/Headers/;
  ls -lah /usr/local/Cellar/qt@4/"${QT_4_VERSION}"/lib/QtGui.framework/Versions/4/Headers/;
  # For Qt4, QtWidgets headers are inside QtGui folder.

  echo "Create paths for Qt.";
  mkdir -p /usr/local/include/Qt/Qt/;
  mkdir -p /usr/local/include/Qt/QtCore/;
  mkdir -p /usr/local/include/Qt/QtGui/;
  mkdir -p /usr/local/include/Qt/QtWidgets/;

  echo "Create symbolic links for Qt 5 headers.";
  # rsync -av --keep-dirlinks /usr/local/Cellar/qt@5/"${QT_5_VERSION}"/lib/QtCore.framework/Versions/5/Headers/ /usr/local/include/Qt/QtCore;
  # rsync -av --keep-dirlinks /usr/local/Cellar/qt@5/"${QT_5_VERSION}"/lib/QtGui.framework/Versions/5/Headers/ /usr/local/include/Qt/QtGui;
  # rsync -av --keep-dirlinks /usr/local/Cellar/qt@5/"${QT_5_VERSION}"/lib/QtWidgets.framework/Versions/5/Headers/ /usr/local/include/Qt/QtWidgets;

  echo "Create symbolic links for Qt 4 headers.";
  rsync -av --keep-dirlinks /usr/local/Cellar/qt@4/"${QT_4_VERSION}"/lib/QtCore.framework/Versions/4/Headers/ /usr/local/include/Qt/QtCore;
  rsync -av --keep-dirlinks /usr/local/Cellar/qt@4/"${QT_4_VERSION}"/lib/QtGui.framework/Versions/4/Headers/ /usr/local/include/Qt/QtGui;
  rsync -av --keep-dirlinks /usr/local/Cellar/qt@4/"${QT_4_VERSION}"/lib/QtGui.framework/Versions/4/Headers/ /usr/local/include/Qt/QtWidgets;

  echo "Searching for some Qt widgets headers.";
  find ~/ -iname "qtwidgetsglobal.h" 2> /dev/null;

  echo "Create symbolic links to mix all Qt headers."
  rsync -av --keep-dirlinks /usr/local/include/Qt/QtCore/ /usr/local/include/Qt/Qt;
  rsync -av --keep-dirlinks /usr/local/include/Qt/QtGui/ /usr/local/include/Qt/Qt;
  rsync -av --keep-dirlinks /usr/local/include/Qt/QtWidgets/ /usr/local/include/Qt/Qt;

  echo "Update include and library paths.";
  export CPLUS_INCLUDE_PATH="/usr/local/include/Qt/:${CPLUS_INCLUDE_PATH}";
  export CPLUS_INCLUDE_PATH="/usr/local/include/Qt/QtCore/:${CPLUS_INCLUDE_PATH}";
  export CPLUS_INCLUDE_PATH="/usr/local/include/Qt/QtGui/:${CPLUS_INCLUDE_PATH}";
  export CPLUS_INCLUDE_PATH="/usr/local/include/Qt/QtWidgets/:${CPLUS_INCLUDE_PATH}";
  # export LIBRARY_PATH="/usr/local/Cellar/qt@5/"${QT_5_VERSION}"/lib/:${LIBRARY_PATH}";



  # export LDFLAGS="-L/usr/local/opt/qt@5/lib";
  # export CPPFLAGS="-I/usr/local/opt/qt@5/include";
  # export PKG_CONFIG_PATH="/usr/local/opt/qt@5/lib/pkgconfig";

  echo "Check Qt files.";
  ls -lah /usr/local/include/Qt/QtWidgets/;
  # ls -lahR /usr/local/Cellar/qt@5/ | grep -ine "\.a" -ine "\.so" -ine "\.dylib";
  # lipo -info /usr/local/Cellar/qt@5/"${QT_5_VERSION}"/Frameworks/QtCore.framework;
}

# Generate code coverage.
function generateCodeCoverage() {
  lcov -c -d . --no-external -o code_coverage_test.info;
  lcov -a code_coverage_base.info -a code_coverage_test.info -o code_coverage.info;
  lcov --remove code_coverage.info -o code_coverage.info '*third_party*' '*build*';
  genhtml code_coverage.info -o code_coverage_report --no-branch-coverage -t MobileRT_code_coverage;
}

###############################################################################
###############################################################################
