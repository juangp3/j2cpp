// Natives of java.util.concurrent.atomic.AtomicBoolean (see AtomicInteger-native.cpp)
#include <java/util/concurrent/atomic/AtomicBoolean.hpp>

bool java::util::concurrent::atomic::AtomicBoolean::getAndSet(bool newValue)
{
    return value.exchange(newValue);
}

bool java::util::concurrent::atomic::AtomicBoolean::compareAndSet(bool expectedValue, bool newValue)
{
    return value.compare_exchange_strong(expectedValue, newValue);
}
