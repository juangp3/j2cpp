// Natives of java.lang.Thread, on POSIX threads.
//
// With J2C_GC_BOEHM, gc.h (with GC_THREADS) turns pthread_create into
// GC_pthread_create, which registers the new thread with the collector so
// its stack is scanned, and keeps the argument reachable until the thread
// runs. std::thread cannot be used there: its state object would come from
// the collector's heap and be reachable from nowhere until the new thread
// had registered.
#include <pthread.h>
#include <sched.h>

#ifdef J2C_GC_BOEHM
#define GC_THREADS
#include <gc.h>
#endif

#include <java/lang/Thread.hpp>

#include "j2c_threads.hpp"

namespace {

// Thread objects are kept reachable by the registry (j2c_thread_started),
// or, for the main thread, by j2c_main_thread: the collector may not scan
// thread-local storage
thread_local java::lang::Thread* current = nullptr;

extern "C" void* threadMain(void* argument)
{
    auto thread = static_cast<java::lang::Thread*>(argument);
    current = thread;
    j2c_interrupt_flag = &thread->interrupted_;
    // j2cRun catches every Throwable and marks the thread terminated
    thread->j2cRun();
    j2c_thread_ended(thread);
    return nullptr;
}

}

// The main thread's Thread, in static data where the collector looks. External
// and volatile, so the optimizer keeps the store that nothing reads (without
// it, -O2 dropped the store and the collector freed the main thread's Thread,
// whose interrupt flag wait() then read as garbage).
java::lang::Thread* volatile j2c_main_thread = nullptr;

java::lang::Thread* java::lang::Thread::currentThread()
{
    clinit();
    if (current == nullptr) {
        // The main thread, the first time it asks
        current = j2cMainThread();
        j2c_main_thread = current;
        j2c_interrupt_flag = &current->interrupted_;
    }
    return current;
}

void java::lang::Thread::yield()
{
    clinit();
    sched_yield();
}

bool java::lang::Thread::start0(bool daemon)
{
    j2c_thread_started(this, daemon);
    pthread_attr_t attributes;
    pthread_attr_init(&attributes);
    pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
    pthread_t id;
    int error = pthread_create(&id, &attributes, threadMain, this);
    pthread_attr_destroy(&attributes);
    if (error != 0) {
        j2c_thread_ended(this);
        return false;
    }
    return true;
}

void java::lang::Thread::interrupt0()
{
    j2c_monitor_interrupt(&interrupted_);
}

bool java::lang::Thread::clearInterrupt()
{
    return interrupted_.exchange(false);
}

bool java::lang::Thread::holdsLock0(Object* obj)
{
    clinit();
    return j2c_holds_lock(obj);
}
