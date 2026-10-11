// Natives of java.lang.System
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <cstring>

#include <Array.hpp>
#include <ObjectArray.hpp>
#include <java/lang/System.hpp>

namespace {

template<typename A>
bool copyPrimitive(java::lang::Object* src, int32_t srcPos, java::lang::Object* dest,
    int32_t destPos, int32_t length, int32_t* result)
{
    auto from = dynamic_cast<A*>(src);
    if (from == nullptr) {
        return false;
    }
    auto to = dynamic_cast<A*>(dest);
    if (to == nullptr) {
        *result = 1;
        return true;
    }
    // memmove copies overlapping ranges as if through a temporary array
    std::memmove(to->begin() + destPos, from->begin() + srcPos,
        static_cast<size_t>(length) * sizeof(typename A::value_type));
    *result = 0;
    return true;
}

template<typename A>
bool lengthOf(java::lang::Object* array, int32_t* result)
{
    auto a = dynamic_cast<A*>(array);
    if (a == nullptr) {
        return false;
    }
    *result = a->length;
    return true;
}

}

int32_t java::lang::System::arrayLength(Object* array)
{
    clinit();
    int32_t result = -1;
    lengthOf<::boolArray>(array, &result) || lengthOf<::char16_tArray>(array, &result)
        || lengthOf<::int8_tArray>(array, &result) || lengthOf<::int16_tArray>(array, &result)
        || lengthOf<::int32_tArray>(array, &result) || lengthOf<::int64_tArray>(array, &result)
        || lengthOf<::floatArray>(array, &result) || lengthOf<::doubleArray>(array, &result)
        || lengthOf<::java::lang::ObjectArray>(array, &result);
    return result;
}

int32_t java::lang::System::arraycopy0(Object* src, int32_t srcPos, Object* dest, int32_t destPos, int32_t length)
{
    clinit();
    int32_t result = 1;
    if (length == 0) {
        // An empty Java array has no storage, and memmove needs real pointers
        return arrayLength(src) >= 0 && arrayLength(dest) >= 0 ? 0 : 1;
    }
    if (copyPrimitive<::boolArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::char16_tArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::int8_tArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::int16_tArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::int32_tArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::int64_tArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::floatArray>(src, srcPos, dest, destPos, length, &result)
        || copyPrimitive<::doubleArray>(src, srcPos, dest, destPos, length, &result)) {
        return result;
    }
    auto from = dynamic_cast<::java::lang::ObjectArray*>(src);
    auto to = dynamic_cast<::java::lang::ObjectArray*>(dest);
    if (from == nullptr || to == nullptr) {
        return 1;
    }
    // set() checks each store, as Java's array store check does
    if (from == to && srcPos < destPos) {
        for (int32_t i = length - 1; i >= 0; i--) {
            to->set(destPos + i, from->get(srcPos + i));
        }
    } else {
        for (int32_t i = 0; i < length; i++) {
            to->set(destPos + i, from->get(srcPos + i));
        }
    }
    return 0;
}

int64_t java::lang::System::currentTimeMillis()
{
    clinit();
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
}

int64_t java::lang::System::nanoTime()
{
    clinit();
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

int32_t java::lang::System::identityHashCode(Object* x)
{
    clinit();
    return x == nullptr ? 0 : static_cast<int32_t>(reinterpret_cast<std::uintptr_t>(x) >> 3);
}

void java::lang::System::exit(int32_t status)
{
    clinit();
    std::exit(status);
}
