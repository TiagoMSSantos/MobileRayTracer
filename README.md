# Ray tracer for Android

![alt text](Example.gif)

## TODO

### Ray tracing engine
* Implement loading of textures
* Separate Material from Primitive in order to save memory
* Implement KD-Tree
* Improve BVH
* Add ray packet intersections
* Add gpu ray tracing support for comparison
* Add more types of shapes
* Support more types of models besides .obj files

### Ray tracing JNI layer
* Refactor DrawView translation unit

### Ray tracing shaders
* Add Bidirectional Path Tracing
* Add Metropolis light transport
* Add shader for debug purposes (wireframe of shapes and boxes)

### Ray tracing test cases
* Prepare more scene models with Blender for testing

### User Interface
* Fix memory leak in Java UI
* Fix load of obj files in Android 10
* Change Linux's UI from GTK to Qt

### System
* Add comments in the code
* Give out of memory error when the memory is not enough to load the scene
* Add unit tests (more code coverage)
* Add system tests
* Add git hooks to check git commit messages
* Add git hooks to submit Jenkins' jobs after each git push

### Documentation
* Improve README
* Write documentation
* Update gif image
* Benchmark against popular ray tracers like PBRT
