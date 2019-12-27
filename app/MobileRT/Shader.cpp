#include "MobileRT/Shader.hpp"
#include "MobileRT/Utils.hpp"
#include <array>
#include <glm/glm.hpp>
#include <glm/gtc/constants.hpp>
#include <random>
#include <utility>

using ::MobileRT::BVH;
using ::MobileRT::Camera;
using ::MobileRT::Intersection;
using ::MobileRT::Ray;
using ::MobileRT::Primitive;
using ::MobileRT::Shader;
using ::MobileRT::Plane;
using ::MobileRT::Sphere;
using ::MobileRT::Triangle;
using ::MobileRT::Light;

namespace {
    const ::std::uint32_t MASK {0xFFFFF};
    const ::std::uint32_t SIZE {MASK + 1};
    ::std::array<float, SIZE> VALUES {};

    bool FillThings() {
        for (auto it {VALUES.begin()}; it < VALUES.end(); std::advance(it, 1)) {
            const ::std::uint32_t index {static_cast<::std::uint32_t>(::std::distance(VALUES.begin(), it))};
            *it = ::MobileRT::haltonSequence(index, 2);
        }
        static ::std::random_device randomDevice {"/dev/urandom"};
        static ::std::mt19937 generator {randomDevice()};
        ::std::shuffle(VALUES.begin(), VALUES.end(), generator);
        return true;
    }
}//namespace

Shader::Shader(Scene scene, const ::std::uint32_t samplesLight, const Accelerator accelerator) noexcept :
        accelerator_ {accelerator},
        samplesLight_ {samplesLight} {
    static bool unused{FillThings()};
    static_cast<void> (unused);
    initializeAccelerators(::std::move(scene));
}

void Shader::initializeAccelerators(Scene scene) noexcept {
    switch (this->accelerator_) {
        case Accelerator::ACC_NONE: {
            break;
        }

        case Accelerator::ACC_NAIVE: {
            this->naivePlanes_ = ::MobileRT::Naive<Plane> {::std::move(scene.planes_)};
            this->naiveSpheres_ = ::MobileRT::Naive<Sphere> {::std::move(scene.spheres_)};
            this->naiveTriangles_ = ::MobileRT::Naive<Triangle> {::std::move(scene.triangles_)};
            break;
        }

        case Accelerator::ACC_REGULAR_GRID: {
            ::glm::vec3 minPlanes {RayLengthMax};
            ::glm::vec3 maxPlanes {-RayLengthMax};
            ::glm::vec3 minSpheres {RayLengthMax};
            ::glm::vec3 maxSpheres {-RayLengthMax};
            ::glm::vec3 minTriangles {RayLengthMax};
            ::glm::vec3 maxTriangles {-RayLengthMax};

            Scene::getBounds<Primitive<Plane>> (scene.planes_, &minPlanes, &maxPlanes);
            Scene::getBounds<Primitive<Sphere>> (scene.spheres_, &minSpheres, &maxSpheres);
            Scene::getBounds<Primitive<Triangle>> (scene.triangles_, &minTriangles, &maxTriangles);

            const AABB sceneBoundsPlanes {minPlanes, maxPlanes};
            const AABB sceneBoundsSpheres {minSpheres, maxSpheres};
            const AABB sceneBoundsTriangles {minTriangles, maxTriangles};

            this->gridPlanes_ = ::MobileRT::RegularGrid<::MobileRT::Plane> {
                    sceneBoundsPlanes, 32, ::std::move(scene.planes_)
            };
            this->gridSpheres_ = ::MobileRT::RegularGrid<::MobileRT::Sphere> {
                    sceneBoundsSpheres, 32, ::std::move(scene.spheres_)
            };
            this->gridTriangles_ = ::MobileRT::RegularGrid<::MobileRT::Triangle> {
                    sceneBoundsTriangles, 32, ::std::move(scene.triangles_)
            };
            break;
        }

        case Accelerator::ACC_BVH: {
            this->bvhPlanes_ = ::MobileRT::BVH<Plane> {::std::move(scene.planes_)};
            this->bvhSpheres_ = ::MobileRT::BVH<Sphere> {::std::move(scene.spheres_)};
            this->bvhTriangles_ = ::MobileRT::BVH<Triangle> {::std::move(scene.triangles_)};
            break;
        }
    }
    this->lights_ = ::std::move(scene.lights_);
}

Shader::~Shader() noexcept {
    LOG("SHADER DELETED");
}

bool Shader::shadowTrace(Intersection intersection, const Ray &ray) noexcept {
    const float lastDist {intersection.length_};
    switch (this->accelerator_) {
        case Accelerator::ACC_NONE: {
            break;
        }

        case Accelerator::ACC_NAIVE: {
            intersection = this->naivePlanes_.shadowTrace(intersection, ray);
            intersection = this->naiveSpheres_.shadowTrace(intersection, ray);
            intersection = this->naiveTriangles_.shadowTrace(intersection, ray);
            break;
        }

        case Accelerator::ACC_REGULAR_GRID: {
            intersection = this->gridPlanes_.shadowTrace(intersection, ray);
            intersection = this->gridSpheres_.shadowTrace(intersection, ray);
            intersection = this->gridTriangles_.shadowTrace(intersection, ray);
            break;
        }

        case Accelerator::ACC_BVH: {
            intersection = this->bvhPlanes_.shadowTrace(intersection, ray);
            intersection = this->bvhSpheres_.shadowTrace(intersection, ray);
            intersection = this->bvhTriangles_.shadowTrace(intersection, ray);
            break;
        }
    }
    const bool res {intersection.length_ < lastDist};
    return res;
}

bool Shader::rayTrace(::glm::vec3 *rgb, const Ray &ray) noexcept {
    Intersection intersection {RayLengthMax, nullptr};
    const float lastDist {intersection.length_};
    switch (this->accelerator_) {
        case Accelerator::ACC_NONE: {
            break;
        }

        case Accelerator::ACC_NAIVE: {
            intersection = this->naivePlanes_.trace(intersection, ray);
            intersection = this->naiveSpheres_.trace(intersection, ray);
            intersection = this->naiveTriangles_.trace(intersection, ray);
            break;
        }

        case Accelerator::ACC_REGULAR_GRID: {
            intersection = this->gridPlanes_.trace(intersection, ray);
            intersection = this->gridSpheres_.trace(intersection, ray);
            intersection = this->gridTriangles_.trace(intersection, ray);
            break;
        }

        case Accelerator::ACC_BVH: {
            intersection = this->bvhPlanes_.trace(intersection, ray);
            intersection = this->bvhSpheres_.trace(intersection, ray);
            intersection = this->bvhTriangles_.trace(intersection, ray);
            break;
        }
    }
    intersection = traceLights(intersection, ray);
    const bool res {intersection.length_ < lastDist && shade(rgb, intersection, ray)};
    return res;
}

Intersection Shader::traceLights(Intersection intersection, const Ray &ray) const noexcept {
    for (const auto &light : this->lights_) {
        intersection = light->intersect(intersection, ray);
    }
    return intersection;
}

void Shader::resetSampling() noexcept {
    for (const auto &light : this->lights_) {
        light->resetSampling();
    }
}

::glm::vec3 Shader::getCosineSampleHemisphere(const ::glm::vec3 &normal) const noexcept {
    static ::std::atomic<::std::uint32_t> sampler {};
    const ::std::uint32_t current1 {sampler.fetch_add(1, ::std::memory_order_relaxed)};
    const ::std::uint32_t current2 {sampler.fetch_add(1, ::std::memory_order_relaxed)};

    const auto it1 {VALUES.begin() + (current1 & MASK)};
    const auto it2 {VALUES.begin() + (current2 & MASK)};

    const float uniformRandom1 {*it1};
    const float uniformRandom2 {*it2};

    const float phi {::glm::two_pi<float>() * uniformRandom1};// random angle around - azimuthal angle
    const float r2 {uniformRandom2};// random distance from center
    const float cosTheta {::std::sqrt(r2)};// square root of distance from center - cos(theta) = cos(elevation angle)

    ::glm::vec3 u {::std::abs(normal[0]) > 0.1F
        ? ::glm::vec3 {0.0F, 1.0F, 0.0F}
        : ::glm::vec3 {1.0F, 0.0F, 0.0F}
    };
    u = ::glm::normalize(::glm::cross(u, normal));// second axis
    const ::glm::vec3 &v {::glm::cross(normal, u)};// final axis

    ::glm::vec3 direction {u * (::std::cos(phi) * cosTheta) +
                           v * (::std::sin(phi) * cosTheta) +
                           normal * ::std::sqrt(1.0F - r2)};
    direction = ::glm::normalize(direction);

    return direction;
}

::std::uint32_t Shader::getLightIndex () {
    static ::std::atomic<::std::uint32_t> sampler {};
    const ::std::uint32_t current {sampler.fetch_add(1, ::std::memory_order_relaxed)};

    const auto it {VALUES.begin() + (current & MASK)};

    const auto sizeLights {static_cast<::std::uint32_t>(this->lights_.size())};
    const float randomNumber {*it};
    const auto chosenLight {static_cast<::std::uint32_t> (::std::floor(randomNumber * sizeLights * 0.99999F))};
    return chosenLight;
}

const ::std::vector<::MobileRT::Primitive<Plane>>& Shader::getPlanes() const noexcept {
    switch (this->accelerator_) {
        case Accelerator::ACC_NONE: {
            return this->naivePlanes_.getPrimitives();
        }

        case Accelerator::ACC_NAIVE: {
            return this->naivePlanes_.getPrimitives();
        }

        case Accelerator::ACC_REGULAR_GRID: {
            return this->gridPlanes_.getPrimitives();
        }

        case Accelerator::ACC_BVH: {
            return this->bvhPlanes_.getPrimitives();
        }
    }
}

const ::std::vector<::MobileRT::Primitive<Sphere>>& Shader::getSpheres() const noexcept {
    switch (this->accelerator_) {
        case Accelerator::ACC_NONE: {
            return this->naiveSpheres_.getPrimitives();
        }

        case Accelerator::ACC_NAIVE: {
            return this->naiveSpheres_.getPrimitives();
        }

        case Accelerator::ACC_REGULAR_GRID: {
            return this->gridSpheres_.getPrimitives();
        }

        case Accelerator::ACC_BVH: {
            return this->bvhSpheres_.getPrimitives();
        }
    }
}

const ::std::vector<::MobileRT::Primitive<Triangle>>& Shader::getTriangles() const noexcept {
    switch (this->accelerator_) {
        case Accelerator::ACC_NONE: {
            return this->naiveTriangles_.getPrimitives();
        }

        case Accelerator::ACC_NAIVE: {
            return this->naiveTriangles_.getPrimitives();
        }

        case Accelerator::ACC_REGULAR_GRID: {
            return this->gridTriangles_.getPrimitives();
        }

        case Accelerator::ACC_BVH: {
            return this->bvhTriangles_.getPrimitives();
        }
    }
}

const ::std::vector<::std::unique_ptr<Light>>& Shader::getLights() const noexcept {
    return this->lights_;
}
