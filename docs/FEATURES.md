# Features

## Ray tracing engine

- [ ] Support for textures with different materials
- [x] Support acceleration structures
- [x] Support OpenGL Mathematics for math library
- [ ] Support Eigen for math library
- [x] Support Google Test for unit tests
- [x] Support Boost for assertions
- [x] Support stb for textures
- [ ] Support Google Benchmark
- [ ] Support Microsoft Guidelines Support Library
- [x] Split Material from Primitive in order to save memory
- [ ] Support GPU ray tracing
- [x] Split naive acceleration structure from Scene
- [ ] Support acceleration structures compatible with the lights

## Shapes

- [x] Support triangle
  - [x] Support normals per vertex
  - [x] Support texture coordinates
  - [x] Support textures
  - [ ] Support ray packet intersections
- [x] Support plane
- [x] Support sphere
- [ ] Support square / rectangle

## Acceleration structures

- [x] Support Axis Aligned Bounding Box
- [x] Support Naive
- [x] Support Regular Grid
  - [x] Parallelize build
- [x] Support Bounding Volume Hierarchy
  - [ ] Parallelize build
  - [ ] Support ray packet intersections
- [ ] Support KD-Tree
- [x] Make all acceleration structures as templates

## Shaders

- [x] Support DepthMap
- [x] Support only material color
- [x] Support simple shader without shadows
- [x] Support Whitted
- [x] Support Path Tracing
  - [ ] Fix algorithm
- [ ] Support Bidirectional Path Tracing
- [ ] Support Metropolis light transport
- [ ] Support Wireframe shader
- [ ] Fix refractions
- [ ] Improve shaders performance

## Samplers

- [x] Support constant
- [x] Support stratified
- [x] Support Halton Sequence
- [x] Support Mersenne Twister
- [x] Support PCG

## Cameras

- [x] Support orthographic Camera
- [x] Support perspective Camera
- [ ] Support fisheye Camera

## Loaders

- [x] Support Wavefront OBJ
  - [ ] Optimize code
- [ ] Support Autodesk FBX
- [ ] Support CAD STL
- [ ] Support PLY

## Lights

- [x] Support point light
- [x] Support triangle area light
- [ ] Support sphere area light
- [ ] Support plane area light

## Ray tracing JNI layer

- [ ] Refactor DrawView translation unit
- [ ] Improve DrawView code readability
- [x] Fix race conditions

## Android Interface

- [x] Compatible with Android 4.1 (API 16)
- [x] Support preview (rasterization of 1 frame)
- [x] Support any OpenGL framerate
- [x] Support reset button
- [ ] Support export rendered image
- [x] Fix memory leak in Java UI
- [x] Fix load of obj files in Android 10
- [x] Improve Java UI code to more Object Oriented
- [ ] Change Android icon
- [x] Remove usage of deprecated methods
- [x] Add Android instrumented unit tests
- [x] Make Android instrumented tests run on debug and release
- [x] Make all Android instrumented tests pass without flakiness

## Linux Interface

- [x] Support Linux's UI with Qt 4
- [x] Support Linux's UI with Qt 5
- [x] Support options menu in GUI
- [x] Support about menu in GUI
- [x] Support for selection of OBJ files in GUI
- [ ] Support selection of all ray tracer options in GUI

## Building process

- [x] Compatible with C++11 compilers
- [ ] Compatible with C++03 compilers
- [x] Compatible with GNU C++ Compiler (g++)
- [x] Compatible with Clang++
- [x] Compatible with MinGW (g++)
- [x] Compatible with Microsoft Visual C++ (MSVC)
- [x] Support compiler warnings for clang / g++ in CMake
- [x] Support compiler warnings for Java
- [x] Support warnings for Gradle
- [x] Support rules with optimizations for proguard
- [x] Support C++ compilation with optimization flags
- [x] Support C++ exceptions
- [x] Support C++ OpenMP
- [x] Support CMake jobs to improve building time
- [x] Support CMake to clone the third party repositories
- [x] Support CMake to pull new versions of third party repositories
- [x] Split the Android application in 3 layers:
  - [x] MobileRT (ray tracing engine)
  - [x] Rendering components (cameras, lights, loaders, samplers, shaders)
  - [x] UI (Android through JNI, Linux through Qt)

## System

- [x] Support unit tests (code coverage)
- [ ] Support to export rendered image to a file
- [ ] Support to store rendered image to a database
- [ ] Support to load image from a database
- [ ] Support to continue rendering an image loaded from a database
- [ ] Remove MobileRT duplicated code
- [ ] Remove Components duplicated code
- [ ] Remove Android JNI duplicated code
- [ ] Remove Android UI duplicated code
- [ ] Remove Qt duplicated code
- [ ] Remove Java tests duplicated code
- [ ] Remove C++ tests duplicated code
- [x] Add message reasons to all assertions
- [x] Load lights and cameras from files
- [ ] Prepare more scene models
- [x] Support Android interface
- [x] Support Linux interface
- [x] Support MacOS X interface
- [x] Support Windows interface
- [ ] Support web browser interface
- [ ] Support Clang format

## Docker

- [x] Make a docker image with MobileRT
  - [x] Create image based on ubuntu
  - [x] Create image based on alpine
  - [x] Create image based on centos
  - [x] Create image based on archlinux
  - [x] Create image based on gentoo
- [x] Add an example model to the docker image
- [ ] Make the ray tracer distribute the load across different engines
- [ ] Use docker compose to launch multiple containers and distribute the load

## Documentation

- [x] Support doxygen documentation in the MobileRT
- [ ] Support doxygen documentation in the Components
- [ ] Support doxygen documentation in the JNI layer
- [ ] Support doxygen documentation in the Qt interface
- [x] Support javadoc in the Android UI
- [x] Add comments for readability in the native building files
- [ ] Add comments for readability in the gradle building files
- [x] Add comments for readability in the scripts

## Broadcast bitmap to multiple desktop devices

- [ ] Support Android interface to send updated bitmap regularly to Kafka topic via REST API
- [ ] Develop small pc application that subscribes to that topic via KAFKA client
- [ ] Support application to show bitmap in every update

## Broadcast bitmap to multiple Android devices

- [ ] Support Linux interface to send updated bitmap regularly to Kafka topic via KAFKA client
- [ ] Support Android interface to subscribe to that that topic via REST API
- [ ] Support Android interface to show bitmap in every update

## Tests

- [x] C++ unit tests
- [x] Android instrumentation tests
- [ ] C++ end to end tests (selenium)
- [ ] Android performance tests
- [ ] C++ performance tests
- [x] Support CI/CD pipeline
  - [x] Support linter
  - [x] Support Android
  - [x] Support Linux
  - [x] Support MacOS
  - [x] Support Windows
  - [x] Support Docker with multiple Linux distributions

## Infrastructure

- [x] Create Makefile to boot local K8s cluster
- [x] Create Helm Chart for MobileRT
- [ ] Create MobileRT web server
  - [ ] Use Rust
  - [ ] Use Tokio framework
- [ ] Deploy MobileRT into cloud server
  - [ ] Deploy into AWS EC2 instance
- [ ] Deploy MobileRT into Kubernetes environment
  - [ ] Deploy into AWS EKS cluster
