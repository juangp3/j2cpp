// Natives of java.util.concurrent.atomic.AtomicLong (see AtomicInteger-native.cpp)
#include <java/util/concurrent/atomic/AtomicLong.hpp>

int64_t java::util::concurrent::atomic::AtomicLong::getAndSet(int64_t newValue)
{
    return value.exchange(newValue);
}

bool java::util::concurrent::atomic::AtomicLong::compareAndSet(int64_t expectedValue, int64_t newValue)
{
    return value.compare_exchange_strong(expectedValue, newValue);
}

int64_t java::util::concurrent::atomic::AtomicLong::getAndAdd(int64_t delta)
{
    return value.fetch_add(delta);
}
