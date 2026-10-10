// Support for the GoogleTest files J2C generates from JUnit tests. A Java
// test fails when it throws, so the generated tests run the translated Java
// code through callJava and report any Java exception it throws.
#pragma once

#include <map>
#include <memory>
#include <string>
#include <typeinfo>

#include <gtest/gtest.h>

#if __has_include(<cxxabi.h>)
#include <cstdlib>
#include <cxxabi.h>
#endif

#include <java/lang/Throwable.hpp>

namespace j2c {

/** Runs Java code; returns the Java exception it threw, or nullptr */
template<typename F>
::java::lang::Throwable* callJava(F&& code)
{
    try {
        code();
    } catch (::java::lang::Throwable* thrown) {
        return thrown;
    }
    return nullptr;
}

/** The Java class name of an exception, such as java.lang.AssertionError */
inline std::string describe(::java::lang::Throwable* thrown)
{
    std::string name = typeid(*thrown).name();
#if __has_include(<cxxabi.h>)
    int status = 0;
    std::unique_ptr<char, void (*)(void*)> demangled(
        abi::__cxa_demangle(name.c_str(), nullptr, nullptr, &status), std::free);
    if (status == 0) {
        name = demangled.get();
    }
#endif
    for (std::string::size_type at = name.find("::"); at != std::string::npos;
         at = name.find("::", at)) {
        name.replace(at, 2, ".");
    }
    return name;
}

}
