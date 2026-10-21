// Natives of java.lang.String
#include <mutex>

#include <java/lang/String.hpp>

java::lang::String* java::lang::String::intern()
{
    // Threads share the intern table, and every string literal passes here.
    // Recursive, as internLocked may run String's static initializer, whose
    // literals intern too. Never destroyed: daemon threads may still run
    // while the process exits.
    static std::recursive_mutex* mutex = new std::recursive_mutex();
    std::lock_guard<std::recursive_mutex> guard(*mutex);
    return internLocked();
}
