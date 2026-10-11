// Support for the GoogleTest files J2C generates from JUnit tests. A Java
// test fails when it throws, so the generated tests run the translated Java
// code through run() and report any Java exception it threw; a failed
// assumption (j2c_junit.hpp) skips the test, as in JUnit.
#pragma once

#include <map>
#include <string>
#include <typeinfo>

#include <gtest/gtest.h>

#include <j2c_junit.hpp>
#include <java/lang/Throwable.hpp>

namespace j2c {

/** How running some Java code ended */
struct Outcome {
    /** The Java exception thrown, or nullptr */
    ::java::lang::Throwable* thrown = nullptr;
    /** Whether a JUnit assumption failed */
    bool aborted = false;
};

template<typename F>
Outcome run(F&& code)
{
    Outcome outcome;
    try {
        code();
    } catch (::java::lang::Throwable* thrown) {
        outcome.thrown = thrown;
    } catch (const junit::TestAborted&) {
        outcome.aborted = true;
    }
    return outcome;
}

/** The Java class name of an exception, such as java.lang.AssertionError */
inline std::string className(::java::lang::Throwable* thrown)
{
    return junit::javaName(typeid(*thrown));
}

/** The exception's class, plus the assertion message when it has one */
inline std::string describe(::java::lang::Throwable* thrown)
{
    std::string message = junit::failureMessage(thrown);
    return message.empty() ? className(thrown) : className(thrown) + ": " + message;
}

}
