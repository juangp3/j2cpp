// Natives of java.lang.Object
#include <cstdint>

#include <java/lang/Object.hpp>

java::lang::Object* java::lang::Object::clone()
{
    // Arrays override clone(); other classes need Cloneable support
    return nullptr;
}

java::lang::Class* java::lang::Object::getClass()
{
    return getClass0();
}

int32_t java::lang::Object::hashCode()
{
    return static_cast<int32_t>(reinterpret_cast<std::uintptr_t>(this) >> 3);
}
