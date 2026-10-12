// Natives of java.lang.Float
#include <cstring>

#include <java/lang/Float.hpp>
#include <java/lang/String.hpp>

#include "j2c_runtime.hpp"

java::lang::String* java::lang::Float::toString(float f)
{
    clinit();
    return j2c_string(j2c_floating_text(f, true));
}

int32_t java::lang::Float::floatToRawIntBits(float value)
{
    clinit();
    int32_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

float java::lang::Float::intBitsToFloat(int32_t bits)
{
    clinit();
    float value;
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}
