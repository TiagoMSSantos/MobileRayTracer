#ifndef MOBILERT_ACCELERATORS_BVH_HPP
#define MOBILERT_ACCELERATORS_BVH_HPP

#include "MobileRT/Accelerators/AABB.hpp"
#include "MobileRT/Intersection.hpp"
#include "MobileRT/Scene.hpp"
#include <algorithm>
#include <array>
#include <glm/glm.hpp>
#include <random>
#include <vector>

namespace MobileRT {

    struct BVHNode {
        AABB box_ {};
        ::std::int32_t indexOffset_ {};
        ::std::int32_t numPrimitives_ {};
    };

    template<typename T>
    class BVH final {
        private:
            static const ::std::int32_t maxLeafSize {2};

            struct BuildNode {
                AABB box_ {};
                ::glm::vec3 midPoint_ {};
                ::std::int32_t oldIndex_ {};

                explicit BuildNode(AABB &&box, ::glm::vec3 &&midPoint, const ::std::int32_t oldIndex) noexcept :
                    box_ {box},
                    midPoint_ {midPoint},
                    oldIndex_ {oldIndex} {

                }
            };

        private:
            ::std::vector<BVHNode> boxes_ {};
            ::std::vector<T> primitives_ {};

        private:
            void build(::std::vector<T> &&primitives) noexcept;

            Intersection intersect(Intersection intersection,const Ray &ray, bool shadowTrace = false) noexcept;

            template<typename Iterator>
            ::std::int32_t getSplitIndexSah(Iterator itBegin, Iterator itEnd) noexcept;

            template<typename Iterator>
            ::std::int32_t getMaxAxis(Iterator itBegin, Iterator itEnd) noexcept;

        public:
            explicit BVH() noexcept = default;

            explicit BVH(::std::vector<T> &&primitives) noexcept;

            BVH(const BVH &bvh) noexcept = delete;

            BVH(BVH &&bvh) noexcept = default;

            ~BVH() noexcept;

            BVH &operator=(const BVH &bvh) noexcept = delete;

            BVH &operator=(BVH &&bvh) noexcept = default;

            Intersection trace(Intersection intersection, const Ray &ray) noexcept;

            Intersection shadowTrace(Intersection intersection, const Ray &ray) noexcept;

            const ::std::vector<T>& getPrimitives() const noexcept;
    };



    template<typename T>
    BVH<T>::BVH(::std::vector<T> &&primitives) noexcept {
        if (primitives.empty()) {
            BVHNode bvhNode {};
            this->boxes_.emplace_back(bvhNode);
            return;
        }
        const auto numPrimitives {(primitives.size())};
        const auto maxNodes {numPrimitives * 2 - 1};
        this->boxes_.resize(maxNodes);
        build(::std::move(primitives));
    }

    template<typename T>
    BVH<T>::~BVH() noexcept {
        this->boxes_.clear();
        this->primitives_.clear();

        ::std::vector<BVHNode> {}.swap(this->boxes_);
        ::std::vector<T> {}.swap(this->primitives_);
    }

    template<typename T>
    void BVH<T>::build(::std::vector<T> &&primitives) noexcept {
        ::std::int32_t currentBoxIndex {};
        ::std::int32_t beginBoxIndex {};
        const auto primitivesSize {primitives.size()};
        ::std::int32_t endBoxIndex {static_cast<::std::int32_t> (primitivesSize)};
        ::std::int32_t maxNodeIndex {};

        ::std::array<::std::int32_t, 512> stackBoxIndex {};
        ::std::array<::std::int32_t, 512> stackBoxBegin {};
        ::std::array<::std::int32_t, 512> stackBoxEnd {};

        auto itStackBoxIndex {stackBoxIndex.begin()};
        ::std::advance(itStackBoxIndex, 1);

        auto itStackBoxBegin {stackBoxBegin.begin()};
        ::std::advance(itStackBoxBegin, 1);

        auto itStackBoxEnd {stackBoxEnd.begin()};
        ::std::advance(itStackBoxEnd, 1);

        const auto itBoxes {this->boxes_.begin()};
        const auto itStackBoxIndexBegin {stackBoxIndex.cbegin()};

        ::std::vector<BuildNode> buildNodes {};
        buildNodes.reserve(primitivesSize);
        for (::std::uint32_t i {}; i < primitivesSize; ++i) {
            const auto &primitive {primitives [i]};
            auto &&box {primitive.getAABB()};
            BuildNode &&node {::std::move(box), box.getMidPoint(), static_cast<::std::int32_t> (i)};
            buildNodes.emplace_back(::std::move(node));
        }
        const auto itNodes {buildNodes.begin()};

        do {
            const auto &currentBox {itBoxes + currentBoxIndex};
            const auto boxPrimitivesSize {endBoxIndex - beginBoxIndex};
            const auto itBegin {itNodes + beginBoxIndex};

            const auto itEnd {itNodes + endBoxIndex};
            const auto maxAxis {getMaxAxis(itBegin, itEnd)};
            ::std::sort(itBegin, itEnd,
                [&](const BuildNode &node1, const BuildNode &node2) {
                    return node1.midPoint_[maxAxis] < node2.midPoint_[maxAxis];
                }
            );

            currentBox->box_ = itBegin->box_;
            ::std::vector<AABB> boxes {currentBox->box_};
            boxes.reserve(static_cast<::std::uint32_t> (boxPrimitivesSize));
            for (::std::int32_t i {beginBoxIndex + 1}; i < endBoxIndex; ++i) {
                const AABB &newBox {(itNodes + i)->box_};
                currentBox->box_ = surroundingBox(newBox, currentBox->box_);
                boxes.emplace_back(newBox);
            }

            if (boxPrimitivesSize <= maxLeafSize) {
                currentBox->indexOffset_ = beginBoxIndex;
                currentBox->numPrimitives_ = boxPrimitivesSize;

                ::std::advance(itStackBoxIndex, -1); // pop
                currentBoxIndex = *itStackBoxIndex;
                ::std::advance(itStackBoxBegin, -1); // pop
                beginBoxIndex = *itStackBoxBegin;
                ::std::advance(itStackBoxEnd, -1); // pop
                endBoxIndex = *itStackBoxEnd;
            } else {
                const auto left {maxNodeIndex + 1};
                const auto right {left + 1};
                const auto splitIndex {getSplitIndexSah(boxes.begin(), boxes.end())};

                currentBox->indexOffset_ = left;
                maxNodeIndex = ::std::max(right, maxNodeIndex);

                *itStackBoxIndex = right;
                ::std::advance(itStackBoxIndex, 1); // push
                *itStackBoxBegin = beginBoxIndex + splitIndex;
                ::std::advance(itStackBoxBegin, 1); // push
                *itStackBoxEnd = endBoxIndex;
                ::std::advance(itStackBoxEnd, 1); // push

                currentBoxIndex = left;
                endBoxIndex = beginBoxIndex + splitIndex;
            }
        } while(itStackBoxIndex > itStackBoxIndexBegin);

        LOG("maxNodeId = ", maxNodeIndex);
        this->boxes_.erase (this->boxes_.begin() + maxNodeIndex + 1, this->boxes_.end());
        this->boxes_.shrink_to_fit();
        ::std::vector<BVHNode> {this->boxes_}.swap(this->boxes_);

        this->primitives_.reserve(primitivesSize);
        for (::std::uint32_t i {}; i < primitivesSize; ++i) {
            const auto &node {buildNodes[i]};
            const auto oldIndex {static_cast<::std::uint32_t> (node.oldIndex_)};
            this->primitives_.emplace_back(::std::move(primitives[oldIndex]));
        }
    }

    template<typename T>
    Intersection BVH<T>::trace(Intersection intersection, const Ray &ray) noexcept {
        intersection = intersect(intersection, ray);
        return intersection;
    }

    template<typename T>
    Intersection BVH<T>::shadowTrace(Intersection intersection, const Ray &ray) noexcept {
        intersection = intersect(intersection, ray, true);
        return intersection;
    }

    template<typename T>
    Intersection BVH<T>::intersect(Intersection intersection, const Ray &ray, const bool shadowTrace) noexcept {
        if(this->primitives_.empty()) {
            return intersection;
        }
        ::std::int32_t boxIndex {};
        ::std::array<::std::int32_t, 512> stackBoxIndex {};

        const auto beginBoxIndex {stackBoxIndex.cbegin()};
        auto itStackBoxIndex {stackBoxIndex.begin()};
        ::std::advance(itStackBoxIndex, 1);

        const auto itBoxes {this->boxes_.begin()};
        const auto itPrimitives {this->primitives_.begin()};
        do {
            const auto &node {*(itBoxes + boxIndex)};
            if (node.box_.intersect(ray)) {

                const auto numberPrimitives {node.numPrimitives_};
                if (numberPrimitives > 0) {
                    for (::std::int32_t i {}; i < numberPrimitives; ++i) {
                        auto& primitive {*(itPrimitives + node.indexOffset_ + i)};
                        const auto lastDist {intersection.length_};
                        intersection = primitive.intersect(intersection, ray);
                        if (shadowTrace && intersection.length_ < lastDist) {
                            return intersection;
                        }
                    }
                    ::std::advance(itStackBoxIndex, -1); // pop
                    boxIndex = *itStackBoxIndex;
                } else {
                    const auto left {node.indexOffset_};
                    const auto right {node.indexOffset_ + 1};
                    const auto &childLeft {*(itBoxes + left)};
                    const auto &childRight {*(itBoxes + right)};

                    const auto traverseLeft {childLeft.box_.intersect(ray)};
                    const auto traverseRight {childRight.box_.intersect(ray)};

                    if (!traverseLeft && !traverseRight) {
                        ::std::advance(itStackBoxIndex, -1); // pop
                        boxIndex = *itStackBoxIndex;
                    } else {
                        boxIndex = (traverseLeft) ? left : right;
                        if (traverseLeft && traverseRight) {
                            *itStackBoxIndex = right;
                            ::std::advance(itStackBoxIndex, 1); // push
                        }
                    }
                }

            } else {
                ::std::advance(itStackBoxIndex, -1); // pop
                boxIndex = *itStackBoxIndex;
            }

        } while (itStackBoxIndex > beginBoxIndex);
        return intersection;
    }

    /**
     * Gets the index to where the vector of boxes should be split.
     *
     * @tparam Iterator The type of the iterator.
     * @param itBegin   The iterator of the first box in the vector.
     * @param itEnd     The iterator of the last box in the vector.
     * @return The index where the vector of boxes should be split.
     */
    template<typename T>
    template<typename Iterator>
    ::std::int32_t BVH<T>::getSplitIndexSah(const Iterator itBegin, const Iterator itEnd) noexcept {
        const auto numberBoxes {itEnd - itBegin};
        const auto itBoxes {itBegin};
        const auto numBoxes {numberBoxes - 1};
        const auto sizeUnsigned {static_cast<::std::uint32_t> (numBoxes)};

        ::std::vector<float> leftArea (sizeUnsigned);
        auto leftBox {*itBoxes};
        const auto itLeftArea {leftArea.begin()};
        *itLeftArea = leftBox.getSurfaceArea();
        for (auto i {1}; i < numBoxes; ++i) {
            leftBox = surroundingBox(leftBox, *(itBoxes + i));
            *(itLeftArea + i) = leftBox.getSurfaceArea();
        }

        ::std::vector<float> rightArea (sizeUnsigned);
        auto rightBox {*(itBoxes + numBoxes)};
        const auto itRightArea {rightArea.begin()};
        *(itRightArea + numBoxes - 1) = rightBox.getSurfaceArea();
        for (auto i {numBoxes - 2}; i >= 0; --i) {
            rightBox = surroundingBox(rightBox, *(itBoxes + i + 1));
            *(itRightArea + i) = rightBox.getSurfaceArea();
        }

        auto splitIndex {1};
        auto minSah {*(itLeftArea) + numBoxes * *(itRightArea)};
        for (auto i {1}; i < numBoxes; ++i) {
            const auto nextSplit {i + 1};
            const auto numBoxesLeft {nextSplit};
            const auto numBoxesRight {numberBoxes - numBoxesLeft};
            const auto areaLeft {*(itLeftArea + i)};
            const auto areaRight {*(itRightArea + i)};
            const auto leftSah {numBoxesLeft * areaLeft};
            const auto rightSah {numBoxesRight * areaRight};
            const auto sah {leftSah + rightSah};
            if (sah < minSah) {
                splitIndex = nextSplit;
                minSah = sah;
            }
        }
        return splitIndex;
    }

    template<typename T>
    template<typename Iterator>
    ::std::int32_t BVH<T>::getMaxAxis(const Iterator itBegin, const Iterator itEnd) noexcept {
        auto min {itBegin->box_.pointMin_};
        auto max {itBegin->box_.pointMax_};

        for (auto it {itBegin + 1}; it < itEnd; ::std::advance(it, 1)) {
            const auto &box {it->box_};
            min = ::glm::min(min, box.pointMin_);
            max = ::glm::max(max, box.pointMax_);
        }

        const auto maxDist {max - min};

        const auto maxAxis {
            maxDist[0] >= maxDist[1] && maxDist[0] >= maxDist[2]
            ? 0
            : maxDist[1] >= maxDist[0] && maxDist[1] >= maxDist[2]
                ? 1
                : 2
        };
        return maxAxis;
    }

    template<typename T>
    const ::std::vector<T>& BVH<T>::getPrimitives() const noexcept {
        return this->primitives_;
    }


}//namespace MobileRT

#endif //MOBILERT_ACCELERATORS_BVH_HPP
