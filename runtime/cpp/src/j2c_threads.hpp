// Monitors and threads, shared by the hooks and the natives of Object and
// Thread (j2c_threads.cpp). Nothing here needs a Java class, so programs
// that never start a thread pay only for the monitors they use.
#pragma once

#include <atomic>
#include <cstdint>

namespace java {
namespace lang {
class Object;
}
}

// The hooks that synchronized blocks and methods call (synchronized.hpp):
// a reentrant monitor per object, never null
void lock(java::lang::Object* o);
void unlock(java::lang::Object* o);

// Whether the current thread holds the monitor of o
bool j2c_holds_lock(java::lang::Object* o);

// Object.wait: 0 when notified or timed out (millis 0 waits without a
// limit), 1 when the current thread does not hold the monitor, 2 when the
// thread was interrupted (the interrupt flag is then cleared)
int32_t j2c_monitor_wait(java::lang::Object* o, int64_t millis);

// Object.notify and notifyAll: false when the current thread does not hold
// the monitor
bool j2c_monitor_notify(java::lang::Object* o, bool all);

// The interrupt flag of the current thread, or null for a thread that has
// no Thread object yet (it then cannot be interrupted)
extern thread_local std::atomic<bool>* j2c_interrupt_flag;

// Wakes the waits (Object.wait, and so sleep and join) of the thread whose
// interrupt flag this is; the caller sets the flag first
void j2c_monitor_interrupt(std::atomic<bool>* flag);

// Threads started by Thread.start: the Thread object is kept reachable for
// the collector until j2c_thread_ended. Program exit (atexit, registered by
// init_jvm) waits for every non-daemon thread to end, as the JVM does.
void j2c_thread_started(java::lang::Object* thread, bool daemon);
void j2c_thread_ended(java::lang::Object* thread);
// Threads started and not yet ended
int j2c_threads_alive();

// Registers the exit wait; init_jvm calls it
void j2c_threads_init();

// The exit status main returns: 1 after an uncaught exception
void j2c_set_exit_status(int status);

// System.exit: ends the process at once, as the JVM does, even while other
// threads run or the main thread waits for them
[[noreturn]] void j2c_exit(int status);
