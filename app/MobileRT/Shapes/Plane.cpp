#include "MobileRT/Shapes/Plane.hpp"

using ::MobileRT::AABB;
using ::MobileRT::Plane;
using ::MobileRT::Intersection;

Plane::Plane(const ::glm::vec3 &point, const ::glm::vec3 &normal) noexcept :
    normal_ {::glm::normalize(normal)},
    point_ {point} {
}

Intersection Plane::intersect(const Intersection &intersection, const Ray &ray) const noexcept {
    if (ray.primitive_ == this) {
        return intersection;
    }

    // is ray parallel or contained in the Plane ??
    // planes have two sides!!!
    const float normalizedProjection {::glm::dot(this->normal_, ray.direction_)};
    if (::std::abs(normalizedProjection) < Epsilon) {
        return intersection;
    }

    //https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection
    const ::glm::vec3 vecToPlane {this->point_ - ray.origin_};
    const float scalarProjectionVecToPlaneOnNormal {::glm::dot(this->normal_, vecToPlane)};
    const float distanceToIntersection {scalarProjectionVecToPlaneOnNormal / normalizedProjection};

    // is it in front of the eye?
    // is it farther than the ray length ??
    if (distanceToIntersection < Epsilon || distanceToIntersection >= intersection.length_) {
        return intersection;
    }

    // if so, then we have an intersection
    const ::glm::vec3 intersectionPoint {ray.origin_ + ray.direction_ * distanceToIntersection};
    const Intersection res {intersectionPoint, distanceToIntersection, this->normal_, this};
    return res;
}

::glm::vec3 Plane::getRightVector() const noexcept {
    ::glm::vec3 right {};
    if (this->normal_[0] >= 1) {
        right = ::glm::vec3 {0, 1, 1};
    } else if (this->normal_[1] >= 1) {
        right = ::glm::vec3 {1, 0, 1};
    } else if (this->normal_[2] >= 1) {
        right = ::glm::vec3 {1, 1, 0};
    } else if (this->normal_[0] <= -1) {
        right = ::glm::vec3 {0, 1, 1};
    } else if (this->normal_[1] <= -1) {
        right = ::glm::vec3 {1, 0, 1};
    } else if (this->normal_[2] <= -1) {
        right = ::glm::vec3 {1, 1, 0};
    }
    right = ::glm::normalize(right);
    return right;
}

AABB Plane::getAABB() const noexcept {
    const ::glm::vec3 &rightDir {getRightVector()};
    const ::glm::vec3 &min {this->point_ + rightDir * -100.0F};
    const ::glm::vec3 &max {this->point_ + rightDir * 100.0F};
    const AABB &res {min, max};
    return res;
}

float Plane::distance(const ::glm::vec3 &point) const noexcept {
    //Plane Equation
    //a(x-x0)+b(y-y0)+c(z-z0) = 0
    //abc = normal
    //x0,y0,z0 = point
    //D = |ax0 + by0 + cz0 + d| / sqrt(a² + b² + c²)
    const float d {
            this->normal_[0] * -this->point_[0] +
            this->normal_[1] * -this->point_[1] +
            this->normal_[2] * -this->point_[2]
    };
    const float numerator {this->normal_[0] * point[0] + this->normal_[1] * point[1] + this->normal_[2] * point[2] + d};
    const float denumerator {
        ::std::sqrt(
                this->normal_[0] * this->normal_[0] +
                this->normal_[1] * this->normal_[1] +
                this->normal_[2] * this->normal_[2]
        )
    };
    const float res {numerator / denumerator};
    return res;
}

bool Plane::intersect(const AABB &box) const noexcept {
    const ::glm::vec3 &positiveVertex {box.pointMax_};
    const ::glm::vec3 &negativeVertex {box.pointMin_};

    const float distanceP {distance(positiveVertex)};
    const float distanceN {distance(negativeVertex)};
    const bool res {(distanceP <= 0 && distanceN >= 0) || (distanceP >= 0 && distanceN <= 0)};

    return res;
}
