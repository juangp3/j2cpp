// Natives of java.util.Arrays
#include <ObjectArray.hpp>
#include <java/util/Arrays.hpp>

java::lang::ObjectArray* java::util::Arrays::newArrayLike(::java::lang::ObjectArray* a, int32_t length)
{
    clinit();
    return a->newInstance0(length);
}
