// JUnit assertions for translated tests (j2c --tests). Calls to the JUnit
// assertion and assumption methods are translated into these helpers, which
// follow JUnit's rules: a failed assertion throws java.lang.AssertionError
// (so Java code catching Throwable still sees it) and a failed assumption
// aborts the test. Values that need the Java runtime to print (strings,
// objects) are described by type; user-supplied messages are evaluated but
// not shown until a runtime provides java.lang.String.
#pragma once

#include <bit>
#include <cmath>
#include <cstdint>
#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
#include <typeinfo>

#if __has_include(<cxxabi.h>)
#include <cstdlib>
#include <cxxabi.h>
#endif

#include <java/lang/AssertionError.hpp>
#include <java/lang/Object.hpp>
#include <java/lang/Throwable.hpp>

namespace j2c {
namespace junit {

/** The Java name of a translated class, such as java.lang.AssertionError */
inline std::string javaName(const std::type_info& type)
{
    std::string name = type.name();
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

/** Thrown by failed assumptions; the GoogleTest wrapper skips the test */
struct TestAborted {
};

/** The message of the assertion failure most recently thrown */
struct Failure {
    ::java::lang::Throwable* error = nullptr;
    std::string message;
};

inline Failure& lastFailure()
{
    static thread_local Failure failure;
    return failure;
}

/** The JUnit-style message for an assertion error thrown here, or "" */
inline std::string failureMessage(::java::lang::Throwable* error)
{
    return lastFailure().error == error ? lastFailure().message : std::string();
}

[[noreturn]] inline void fail(const std::string& message)
{
    auto error = new ::java::lang::AssertionError();
    lastFailure().error = error;
    lastFailure().message = message;
    throw error;
}

template<typename T>
std::string show(T value)
{
    std::ostringstream out;
    if constexpr (std::is_pointer_v<T>) {
        if (value == nullptr) {
            return "null";
        }
        out << "an instance of " << javaName(typeid(*value));
    } else if constexpr (std::is_same_v<T, bool>) {
        out << (value ? "true" : "false");
    } else if constexpr (std::is_same_v<T, char16_t>) {
        out << static_cast<std::uint32_t>(value);
    } else if constexpr (std::is_same_v<T, std::int8_t>) {
        out << static_cast<int>(value);
    } else {
        out << value;
    }
    return out.str();
}

inline ::java::lang::Object* object(::java::lang::Object* value)
{
    return value;
}

/** Java's Objects.equals, using the translated equals() */
template<typename T>
bool objectsEqual(T expected, T actual)
{
    if (expected == nullptr || actual == nullptr) {
        return expected == actual;
    }
    return object(expected) == object(actual) || object(expected)->equals(object(actual));
}

/** JUnit compares floating point by bits: NaN equals NaN, 0.0 is not -0.0 */
template<typename T>
bool floatingEqual(T expected, T actual)
{
    if (std::isnan(expected) && std::isnan(actual)) {
        return true;
    }
    if constexpr (std::is_same_v<T, float>) {
        return std::bit_cast<std::uint32_t>(expected) == std::bit_cast<std::uint32_t>(actual);
    } else {
        return std::bit_cast<std::uint64_t>(expected) == std::bit_cast<std::uint64_t>(actual);
    }
}

template<typename T>
bool valuesEqual(T expected, T actual)
{
    if constexpr (std::is_pointer_v<T>) {
        return objectsEqual(expected, actual);
    } else if constexpr (std::is_floating_point_v<T>) {
        return floatingEqual(expected, actual);
    } else {
        return expected == actual;
    }
}

template<typename T, typename... Message>
void assertTrue(bool condition, Message&&...)
{
    if (!condition) {
        fail("expected: <true> but was: <false>");
    }
}

template<typename T, typename... Message>
void assertFalse(bool condition, Message&&...)
{
    if (condition) {
        fail("expected: <false> but was: <true>");
    }
}

template<typename T, typename... Message>
void assertNull(T actual, Message&&...)
{
    if (actual != nullptr) {
        fail("expected: <null> but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertNotNull(T actual, Message&&...)
{
    if (actual == nullptr) {
        fail("expected: not <null>");
    }
}

template<typename T, typename... Message>
void assertEquals(T expected, T actual, Message&&...)
{
    if (!valuesEqual(expected, actual)) {
        fail("expected: <" + show(expected) + "> but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertNotEquals(T unexpected, T actual, Message&&...)
{
    if (valuesEqual(unexpected, actual)) {
        fail("expected: not equal but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertEqualsWithin(T expected, T actual, T delta, Message&&...)
{
    if (!floatingEqual(expected, actual) && !(std::abs(expected - actual) <= delta)) {
        fail("expected: <" + show(expected) + "> but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertNotEqualsWithin(T unexpected, T actual, T delta, Message&&...)
{
    if (floatingEqual(unexpected, actual) || std::abs(unexpected - actual) <= delta) {
        fail("expected: not equal but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertSame(T expected, T actual, Message&&...)
{
    if (object(expected) != object(actual)) {
        fail("expected: <" + show(expected) + "> but was: <" + show(actual) + "> (not the same object)");
    }
}

template<typename T, typename... Message>
void assertNotSame(T unexpected, T actual, Message&&...)
{
    if (object(unexpected) == object(actual)) {
        fail("expected: not same but was: <" + show(actual) + ">");
    }
}

template<typename A, typename Compare>
void compareArrays(A expected, A actual, Compare equal)
{
    if (expected == nullptr || actual == nullptr) {
        if (expected != actual) {
            fail(std::string(expected == nullptr ? "expected array was <null>" : "actual array was <null>"));
        }
        return;
    }
    if (expected->length != actual->length) {
        fail("array lengths differ, expected: <" + show(expected->length) + "> but was: <" + show(actual->length) + ">");
    }
    for (int i = 0; i < expected->length; ++i) {
        if (!equal(expected->get(i), actual->get(i))) {
            fail("array contents differ at index [" + show(i) + "], expected: <" + show(expected->get(i))
                + "> but was: <" + show(actual->get(i)) + ">");
        }
    }
}

template<typename A, typename... Message>
void assertArrayEquals(A expected, A actual, Message&&...)
{
    compareArrays(expected, actual, [](auto e, auto a) { return valuesEqual(e, a); });
}

template<typename A, typename D, typename... Message>
void assertArrayEqualsWithin(A expected, A actual, D delta, Message&&...)
{
    compareArrays(expected, actual, [delta](auto e, auto a) {
        return floatingEqual(e, a) || std::abs(e - a) <= delta;
    });
}

template<typename T, typename... Message>
[[noreturn]] void failTest(Message&&...)
{
    fail("failed");
}

/** JUnit 4's assertEquals(double, double) without a delta always fails */
template<typename T, typename... Arguments>
[[noreturn]] void floatingWithoutDelta(Arguments&&...)
{
    fail("Use assertEquals(expected, actual, delta) to compare floating-point numbers");
}

/** assertThrows(X.class, executable): returns the exception, as JUnit does */
template<typename Expected, typename E, typename... Message>
Expected* assertThrows(const char* expectedName, E executable, Message&&...)
{
    try {
        executable->execute();
    } catch (::java::lang::Throwable* thrown) {
        if (auto expected = dynamic_cast<Expected*>(thrown)) {
            return expected;
        }
        fail(std::string("Unexpected exception type thrown, expected: <") + expectedName
            + "> but was: <" + javaName(typeid(*thrown)) + ">");
    }
    fail(std::string("Expected ") + expectedName + " to be thrown, but nothing was thrown.");
}

template<typename E, typename... Message>
void assertDoesNotThrow(E executable, Message&&...)
{
    try {
        executable->execute();
    } catch (::java::lang::Throwable* thrown) {
        fail(std::string("Unexpected exception thrown: ") + javaName(typeid(*thrown)));
    }
}

template<typename T, typename... Message>
void assumeTrue(bool condition, Message&&...)
{
    if (!condition) {
        throw TestAborted();
    }
}

template<typename T, typename... Message>
void assumeFalse(bool condition, Message&&...)
{
    if (condition) {
        throw TestAborted();
    }
}

}
}
