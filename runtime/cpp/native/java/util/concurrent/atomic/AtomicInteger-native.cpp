// Natives of java.util.concurrent.atomic.AtomicInteger: value is the
// std::atomic that J2C makes of the volatile field. Signed atomic arithmetic
// wraps in C++20, as Java's does.
#include <java/util/concurrent/atomic/AtomicInteger.hpp>

int32_t java::util::concurrent::atomic::AtomicInteger::getAndSet(int32_t newValue)
{
    return value.exchange(newValue);
}

bool java::util::concurrent::atomic::AtomicInteger::compareAndSet(int32_t expectedValue, int32_t newValue)
{
    return value.compare_exchange_strong(expectedValue, newValue);
}

int32_t java::util::concurrent::atomic::AtomicInteger::getAndAdd(int32_t delta)
{
    return value.fetch_add(delta);
}
