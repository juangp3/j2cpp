// Natives of java.lang.Float
#include <java/lang/Float.hpp>
#include <java/lang/String.hpp>

#include "j2c_runtime.hpp"

java::lang::String* java::lang::Float::toString(float f)
{
    clinit();
    return j2c_string(j2c_floating_text(f, true));
}
