// Natives of java.lang.Object
#include <cstdint>

#include <java/lang/Object.hpp>

#include "j2c_threads.hpp"

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

bool java::lang::Object::notify0(bool all)
{
    return j2c_monitor_notify(this, all);
}

int32_t java::lang::Object::wait0(int64_t timeoutMillis)
{
    return j2c_monitor_wait(this, timeoutMillis);
}
