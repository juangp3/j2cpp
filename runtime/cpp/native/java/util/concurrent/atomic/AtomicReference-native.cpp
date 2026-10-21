// Natives of java.util.concurrent.atomic.AtomicReference (see
// AtomicInteger-native.cpp); the comparison is of pointers, as Java's == is
#include <java/util/concurrent/atomic/AtomicReference.hpp>

java::lang::Object* java::util::concurrent::atomic::AtomicReference::getAndSet0(::java::lang::Object* newValue)
{
    return value.exchange(newValue);
}

bool java::util::concurrent::atomic::AtomicReference::compareAndSet0(::java::lang::Object* expectedValue, ::java::lang::Object* newValue)
{
    return value.compare_exchange_strong(expectedValue, newValue);
}
