// Natives of java.lang.Double
#include <cstdlib>
#include <cstring>

#include <java/lang/Double.hpp>
#include <java/lang/String.hpp>

#include "j2c_runtime.hpp"

java::lang::String* java::lang::Double::toString(double d)
{
    clinit();
    return j2c_string(j2c_floating_text(d, false));
}

int64_t java::lang::Double::doubleToRawLongBits(double value)
{
    clinit();
    int64_t bits;
    std::memcpy(&bits, &value, sizeof(bits));
    return bits;
}

double java::lang::Double::longBitsToDouble(int64_t bits)
{
    clinit();
    double value;
    std::memcpy(&value, &bits, sizeof(value));
    return value;
}

double java::lang::Double::convert(String* s, bool toFloat)
{
    clinit();
    std::string text = j2c_ascii(s);
    // Java rounds a float directly from the decimal text, not via double
    return toFloat ? static_cast<double>(std::strtof(text.c_str(), nullptr))
                   : std::strtod(text.c_str(), nullptr);
}
