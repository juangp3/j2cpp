// Monitors (synchronized, Object.wait and notify) and the thread registry
// that program exit waits on; see j2c_threads.hpp.
//
// Monitors live in a striped table instead of in each object, so Object
// keeps no extra field: a monitor is taken from its stripe's free list when
// a thread first locks or waits on an object and goes back when no thread
// holds it, waits for it or waits on it. A stripe's mutex guards its
// monitors and is held only briefly; threads block on the monitors'
// condition variables. Each thread in Object.wait has its own waiter node,
// so notify wakes exactly the longest waiting thread and an interrupt or
// timeout cannot swallow a notification meant for another thread.
//
// With the Boehm collector, monitors come from the collector's heap (the
// global operator new) and stay reachable from the static stripes; the
// waiter nodes live on the waiting threads' stacks.
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstdlib>
#include <mutex>
#include <vector>

#include "j2c_threads.hpp"

thread_local std::atomic<bool>* j2c_interrupt_flag = nullptr;

namespace {

struct Waiter {
    std::condition_variable cv;
    std::atomic<bool>* interrupt = nullptr;
    bool notified = false;
    Waiter* next = nullptr;
};

struct Monitor {
    java::lang::Object* object = nullptr;
    // The holding thread (see self) and its hold count
    const void* owner = nullptr;
    int32_t count = 0;
    // Threads holding, entering or waiting on this monitor
    int32_t users = 0;
    std::condition_variable entry;
    // Threads in Object.wait, in order
    Waiter* head = nullptr;
    Waiter* tail = nullptr;
    Monitor* next = nullptr;
};

struct Stripe {
    std::mutex mutex;
    Monitor* used = nullptr;
    Monitor* free = nullptr;
};

constexpr int stripeBits = 8;
Stripe stripes[1 << stripeBits];

// A per-thread address that identifies the current thread
thread_local char selfMarker;

const void* self()
{
    return &selfMarker;
}

Stripe& stripeOf(java::lang::Object* o)
{
    std::uint64_t a = reinterpret_cast<std::uintptr_t>(o);
    a = (a ^ (a >> 17)) * UINT64_C(0x9E3779B97F4A7C15);
    return stripes[a >> (64 - stripeBits)];
}

Monitor* find(Stripe& s, java::lang::Object* o)
{
    for (Monitor* m = s.used; m != nullptr; m = m->next) {
        if (m->object == o) {
            return m;
        }
    }
    return nullptr;
}

Monitor* findOrCreate(Stripe& s, java::lang::Object* o)
{
    Monitor* m = find(s, o);
    if (m != nullptr) {
        return m;
    }
    if (s.free != nullptr) {
        m = s.free;
        s.free = m->next;
    } else {
        m = new Monitor();
    }
    m->object = o;
    m->next = s.used;
    s.used = m;
    return m;
}

// The monitor is idle: no thread holds, enters or waits on it
void recycle(Stripe& s, Monitor* m)
{
    for (Monitor** p = &s.used; *p != nullptr; p = &(*p)->next) {
        if (*p == m) {
            *p = m->next;
            break;
        }
    }
    m->object = nullptr;
    m->next = s.free;
    s.free = m;
}

void enter(Monitor* m, std::unique_lock<std::mutex>& guard, int32_t count)
{
    while (m->owner != nullptr) {
        m->entry.wait(guard);
    }
    m->owner = self();
    m->count = count;
}

void unlink(Monitor* m, Waiter* w)
{
    Waiter* previous = nullptr;
    for (Waiter* x = m->head; x != nullptr; previous = x, x = x->next) {
        if (x == w) {
            if (previous == nullptr) {
                m->head = w->next;
            } else {
                previous->next = w->next;
            }
            if (m->tail == w) {
                m->tail = previous;
            }
            return;
        }
    }
}

[[noreturn]] void internalError(const char* message)
{
    std::fputs("j2c: ", stderr);
    std::fputs(message, stderr);
    std::fputc('\n', stderr);
    std::abort();
}

}

void lock(java::lang::Object* o)
{
    Stripe& s = stripeOf(o);
    std::unique_lock<std::mutex> guard(s.mutex);
    Monitor* m = findOrCreate(s, o);
    if (m->owner == self()) {
        m->count++;
        return;
    }
    m->users++;
    enter(m, guard, 1);
}

void unlock(java::lang::Object* o)
{
    Stripe& s = stripeOf(o);
    std::unique_lock<std::mutex> guard(s.mutex);
    Monitor* m = find(s, o);
    if (m == nullptr || m->owner != self()) {
        internalError("unlock of a monitor the thread does not hold");
    }
    if (--m->count > 0) {
        return;
    }
    m->owner = nullptr;
    if (--m->users == 0) {
        recycle(s, m);
    } else {
        m->entry.notify_one();
    }
}

bool j2c_holds_lock(java::lang::Object* o)
{
    Stripe& s = stripeOf(o);
    std::lock_guard<std::mutex> guard(s.mutex);
    Monitor* m = find(s, o);
    return m != nullptr && m->owner == self();
}

int32_t j2c_monitor_wait(java::lang::Object* o, int64_t millis)
{
    Stripe& s = stripeOf(o);
    std::unique_lock<std::mutex> guard(s.mutex);
    Monitor* m = find(s, o);
    if (m == nullptr || m->owner != self()) {
        return 1;
    }
    std::atomic<bool>* flag = j2c_interrupt_flag;
    if (flag != nullptr && flag->exchange(false)) {
        return 2;
    }

    Waiter w;
    w.interrupt = flag;
    if (m->tail == nullptr) {
        m->head = &w;
    } else {
        m->tail->next = &w;
    }
    m->tail = &w;

    // Release every hold, as Java does, and let an entering thread in
    int32_t holds = m->count;
    m->owner = nullptr;
    m->count = 0;
    m->entry.notify_one();

    // About 30 years and more: no limit
    bool timed = millis > 0 && millis < INT64_C(1000000000000);
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timed ? millis : 0);
    while (!w.notified && !(flag != nullptr && flag->load())) {
        if (!timed) {
            w.cv.wait(guard);
        } else if (w.cv.wait_until(guard, deadline) == std::cv_status::timeout) {
            break;
        }
    }
    if (!w.notified) {
        unlink(m, &w);
    }

    enter(m, guard, holds);
    // A notified thread returns normally and keeps a later interrupt pending
    if (!w.notified && flag != nullptr && flag->exchange(false)) {
        return 2;
    }
    return 0;
}

bool j2c_monitor_notify(java::lang::Object* o, bool all)
{
    Stripe& s = stripeOf(o);
    std::lock_guard<std::mutex> guard(s.mutex);
    Monitor* m = find(s, o);
    if (m == nullptr || m->owner != self()) {
        return false;
    }
    while (m->head != nullptr) {
        Waiter* w = m->head;
        m->head = w->next;
        if (m->head == nullptr) {
            m->tail = nullptr;
        }
        w->notified = true;
        w->cv.notify_one();
        if (!all) {
            break;
        }
    }
    return true;
}

void j2c_monitor_interrupt(std::atomic<bool>* flag)
{
    for (Stripe& s : stripes) {
        std::lock_guard<std::mutex> guard(s.mutex);
        for (Monitor* m = s.used; m != nullptr; m = m->next) {
            for (Waiter* w = m->head; w != nullptr; w = w->next) {
                if (w->interrupt == flag) {
                    w->cv.notify_one();
                }
            }
        }
    }
}

namespace {

std::mutex liveMutex;
std::condition_variable liveChanged;
int alive = 0;
int nonDaemon = 0;
int exitStatus = 0;
// Started threads, reachable for the collector while they run (the
// vector's buffer comes from its scanned heap), with whether each is a
// daemon (setDaemon works again once a thread ended); never destroyed, so
// daemon threads can still end while the process exits
struct LiveThread {
    java::lang::Object* thread;
    bool daemon;
};
std::vector<LiveThread>* liveThreads = nullptr;

// At exit, as the JVM's DestroyJavaVM: wait for the non-daemon threads.
// Daemon threads that still run are abandoned: the process ends at once,
// before static destructors could run under them.
void awaitThreads()
{
    std::unique_lock<std::mutex> guard(liveMutex);
    liveChanged.wait(guard, [] { return nonDaemon == 0; });
    if (alive > 0) {
        guard.unlock();
        std::fflush(nullptr);
        std::_Exit(exitStatus);
    }
}

}

void j2c_thread_started(java::lang::Object* thread, bool daemon)
{
    std::lock_guard<std::mutex> guard(liveMutex);
    if (liveThreads == nullptr) {
        liveThreads = new std::vector<LiveThread>();
    }
    liveThreads->push_back(LiveThread { thread, daemon });
    alive++;
    if (!daemon) {
        nonDaemon++;
    }
}

void j2c_thread_ended(java::lang::Object* thread)
{
    std::lock_guard<std::mutex> guard(liveMutex);
    for (auto it = liveThreads->begin(); it != liveThreads->end(); ++it) {
        if (it->thread == thread) {
            if (!it->daemon) {
                nonDaemon--;
            }
            liveThreads->erase(it);
            break;
        }
    }
    alive--;
    liveChanged.notify_all();
}

int j2c_threads_alive()
{
    std::lock_guard<std::mutex> guard(liveMutex);
    return alive;
}

void j2c_threads_init()
{
    std::atexit(awaitThreads);
}

void j2c_set_exit_status(int status)
{
    std::lock_guard<std::mutex> guard(liveMutex);
    exitStatus = status;
}

void j2c_exit(int status)
{
    if (j2c_threads_alive() == 0) {
        std::exit(status);
    }
    std::fflush(nullptr);
    std::_Exit(status);
}
