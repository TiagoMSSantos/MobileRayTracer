#!/bin/bash

###############################################################################
# README
###############################################################################
# This script compiles MobileRT (in debug or release mode) for a native
# Operating System by using the CMake to generate the Makefiles, making it
# compatible with:
# * Linux
# * MacOS
# * Windows
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
# Execute Shellcheck on this script.
###############################################################################
if [ -x "$(command -v shellcheck)" ]; then
  shellcheck "${0}" || exit
fi
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
# Set default arguments.
###############################################################################
type="release";
compiler="g++";
recompile="no";
parallelizeBuild;

function printEnvironment() {
  echo "";
  echo "Selected arguments:";
  echo "type: ${type}";
  echo "compiler: ${compiler}";
  echo "recompile: ${recompile}";
}
###############################################################################
###############################################################################


###############################################################################
# Parse arguments.
###############################################################################
parseArgumentsToCompile "$@";
printEnvironment;
###############################################################################
###############################################################################


###############################################################################
# Fix llvm clang OpenMP library.
###############################################################################
function addOpenMpPath() {
  set +e;
  OPENMP_INCLUDE_PATH="$(find /usr/local/Cellar/libomp -iname "omp.h" | head -1 2> /dev/null)";
  OPENMP_LIB_PATH="$(find /usr/local/Cellar/libomp -iname "libomp.dylib" | head -1 2> /dev/null)";
  set -e;
  echo "OPENMP_INCLUDE_PATH = ${OPENMP_INCLUDE_PATH}";
  echo "OPENMP_LIB_PATH = ${OPENMP_LIB_PATH}";
  set +u;
  export CPLUS_INCLUDE_PATH="${OPENMP_INCLUDE_PATH%/omp.h}:${CPLUS_INCLUDE_PATH}";
  export LIBRARY_PATH="${OPENMP_LIB_PATH%/libomp.dylib}:${LIBRARY_PATH}";
  set -u;
}
addOpenMpPath;
###############################################################################
###############################################################################


###############################################################################
# Fix Qt library for MacOs.
###############################################################################
function addQtPath() {
  set +e;
  QT5_INCLUDE_PATHS="$(find /usr/local -name "QDialog" -not -path "*/MobileRT" 2> /dev/null)";
  QT5_INCLUDE_PATH=$(echo "${QT5_INCLUDE_PATHS}" | tail -1);
  QT5_LIB_PATH=${QT5_INCLUDE_PATH%/*/*/*/*/*};
  QT_INCLUDE_PATH="/usr/local/include/Qt/";
  set -e;
  echo "QT_INCLUDE_PATHS = ${QT5_INCLUDE_PATHS}";
  echo "QT_INCLUDE_PATH = ${QT5_INCLUDE_PATH}";
  echo "QT_LIB_PATH = ${QT5_LIB_PATH}";
  set +u;
  export CPLUS_INCLUDE_PATH="${QT_INCLUDE_PATH}:${CPLUS_INCLUDE_PATH}";
  export CPLUS_INCLUDE_PATH="${QT5_INCLUDE_PATH%/*}:${CPLUS_INCLUDE_PATH}";
  export CPLUS_INCLUDE_PATH="${QT5_INCLUDE_PATH%/*/*}:${CPLUS_INCLUDE_PATH}";
  export LIBRARY_PATH="${QT5_LIB_PATH}:${LIBRARY_PATH}";
  set -u;
}
addQtPath;
###############################################################################
###############################################################################


###############################################################################
# Get the proper C compiler for conan.
# Possible values for clang are ['3.3', '3.4', '3.5', '3.6', '3.7', '3.8', '3.9', '4.0',
# '5.0', '6.0', '7.0', '7.1', '8', '9', '10', '11']
# Possible values for gcc (Apple clang) are ['4.1', '4.4', '4.5', '4.6', '4.7', '4.8',
# '4.9', '5', '5.1', '5.2', '5.3', '5.4', '5.5', '6', '6.1', '6.2', '6.3', '6.4',
# '6.5', '7', '7.1', '7.2', '7.3', '7.4', '7.5', '8', '8.1', '8.2', '8.3', '8.4',
# '9', '9.1', '9.2', '9.3', '10', '10.1']
###############################################################################
function addCompilerPath() {
  if [[ "${compiler}" == *"clang++"* ]]; then
    conan_compiler="clang";
    conan_compiler_version=$(${compiler} --version | grep -i version | tr -s ' ' | cut -d ' ' -f 3 | cut -d '-' -f 1 | cut -d '.' -f1,2);
    export CC=clang;
  elif [[ "${compiler}" == *"g++"* ]]; then
    conan_compiler="gcc";
    conan_compiler_version=$(${compiler} -dumpversion | cut -d '.' -f 1,2);
    export CC=gcc;
  fi

  # Flag `-v` not compatible with MSVC.
  #${compiler} -v;
  set +u; # Because of Windows OS doesn't have clang++ nor g++.
  echo "Detected '${conan_compiler}' '${conan_compiler_version}' compiler.";
  set -u;

  export CXX="${compiler}";

  # Fix compiler version used.
  #if [ "${conan_compiler_version}" == "9.0" ]; then
  #  conan_compiler_version=9;
  #fi
  #if [ "${conan_compiler_version}" == "12.0" ]; then
  #  conan_compiler_version=12;
  #fi
  #if [ "${conan_compiler_version}" == "4.2" ]; then
  #  conan_compiler_version=4.0;
  #fi
}

addCompilerPath;
###############################################################################
###############################################################################


###############################################################################
# Get Conan path.
###############################################################################
function addConanPath() {
  if [ ! -x "$(command -v conan)" ]; then
    CONAN_PATH=$(find ~/ -iname "conan" || true);
  fi

  set +u;
  echo "Conan binary: ${CONAN_PATH}";
  echo "Conan location: ${CONAN_PATH%/conan}";
  if [ -n "${CONAN_PATH}" ]; then
    PATH=${CONAN_PATH%/conan}:${PATH};
  fi
  set -u;

  echo "PATH: ${PATH}";
}

addConanPath;
###############################################################################
###############################################################################


###############################################################################
# Get CPU Architecture.
###############################################################################
function setCpuArchitecture() {
  CPU_ARCHITECTURE=x86_64;
  if [ -x "$(command -v uname)" ]; then
    CPU_ARCHITECTURE=$(uname -m);
    if [ "${CPU_ARCHITECTURE}" == "aarch64" ]; then
      CPU_ARCHITECTURE=armv8;
    fi
  fi
}

setCpuArchitecture;
###############################################################################
###############################################################################


###############################################################################
# Add Conan remote dependencies.
###############################################################################
# Install C++ Conan dependencies.
function install_conan_dependencies() {
#  ln -s configure/config.guess /home/travis/.conan/data/libuuid/1.0.3/_/_/build/b818fa1fc0d3879f99937e93c6227da2690810fe/configure/config.guess;
#  ln -s configure/config.sub /home/travis/.conan/data/libuuid/1.0.3/_/_/build/b818fa1fc0d3879f99937e93c6227da2690810fe/configure/config.sub;

  if [ -x "$(command -v conan)" ]; then
    conan profile new default;
    conan profile update settings.compiler="${conan_compiler}" default;
    conan profile update settings.compiler.version="${conan_compiler_version}" default;
    # Possible values for compiler.libcxx are ['libstdc++', 'libstdc++11'].
    conan profile update settings.compiler.libcxx="libstdc++11" default;
    conan profile update settings.arch="${CPU_ARCHITECTURE}" default;
    conan profile update settings.os="Linux" default;
    conan profile update settings.build_type="Release" default;
    conan remote add bintray https://api.bintray.com/conan/bincrafters/public-conan;
  fi

  conan install \
  -s compiler=${conan_compiler} \
  -s compiler.version="${conan_compiler_version}" \
  -s compiler.libcxx=libstdc++11 \
  -s arch="${CPU_ARCHITECTURE}" \
  -s os="Linux" \
  -s build_type=Release \
  -o bzip2:shared=True \
  --build missing \
  --profile default \
  ../app/third_party/conan/Native;

  export CONAN="TRUE";
}
###############################################################################
###############################################################################


###############################################################################
# Compile for native.
###############################################################################
function create_build_folder() {
  # Set path to build.
  build_path=./build_${type};

  type=$(capitalizeFirstletter "${type}");
  echo "type: '${type}'";

  if [ "${recompile}" == "yes" ]; then
    rm -rf "${build_path:?Missing build path}"/*;
  fi
  mkdir -p "${build_path}";
}

function build() {
  create_build_folder;
  cd "${build_path}";
#  install_conan_dependencies;

  echo "Calling CMake";
  cmake -DCMAKE_VERBOSE_MAKEFILE=ON \
    -DCMAKE_CXX_COMPILER="${compiler}" \
    -DCMAKE_BUILD_TYPE="${type}" \
    ../app/ \
    2>&1 | tee ./log_cmake_"${type}".log;
  resCompile=${PIPESTATUS[0]};

  COMPILER=$(grep -i "CMAKE_CXX_COMPILER_ID=" log_cmake_"${type}".log | cut -d '=' -f 2);
  if [ "${COMPILER}" != "MSVC" ]; then
    JOBS_FLAG=${MAKEFLAGS};
  fi

  if [ "${resCompile}" -eq 0 ]; then
    set +u;
    echo "Calling Make with parameters: ${JOBS_FLAG}";
    cmake --build . -- "${JOBS_FLAG}" 2>&1 | tee ./log_make_"${type}".log;
    set -u;
    resCompile=${PIPESTATUS[0]};
  else
    echo "Compilation: cmake failed";
  fi
}
###############################################################################
###############################################################################

build;

###############################################################################
# Exit code.
###############################################################################
printCommandExitCode "${resCompile}" "Compilation";
###############################################################################
###############################################################################
