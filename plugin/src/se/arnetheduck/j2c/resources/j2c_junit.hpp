// JUnit assertions for translated tests (j2c --tests). Calls to the JUnit
// assertion and assumption methods are translated into these helpers, which
// follow JUnit's rules: a failed assertion throws java.lang.AssertionError
// (so Java code catching Throwable still sees it) and a failed assumption
// aborts the test. JUnit 5's helpers are in j2c::junit, JUnit 4's, whose
// messages differ, in j2c::junit::junit4.
//
// With a J2C runtime (j2c --runtime), values are shown as Java shows them
// (String.valueOf, toString), user messages are shown (a Supplier<String>
// message is called only when the assertion fails, as in JUnit), and the
// AssertionError carries the message, as JUnit's does. Without one, objects
// are described by type and user messages are evaluated but not shown.
#pragma once

#include <algorithm>
#include <bit>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <optional>
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

#if __has_include(<j2c_runtime.hpp>)
#define J2C_JUNIT_RUNTIME 1
#include <j2c_runtime.hpp>
#include <java/lang/Class.hpp>
#include <java/lang/String.hpp>
#include <java/lang/System.hpp>
#endif

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

inline ::java::lang::Object* object(::java::lang::Object* value)
{
    return value;
}

/** Text from user messages and values; nullopt is Java's null */
using Text = std::optional<std::string>;

#ifdef J2C_JUNIT_RUNTIME
/** The UTF-8 text of a Java string */
inline std::string utf8(::java::lang::String* s)
{
    std::string out;
    int32_t length = s->length();
    for (int32_t i = 0; i < length; ++i) {
        char32_t c = s->charAt(i);
        if (c >= 0xD800 && c < 0xDC00 && i + 1 < length) {
            char32_t low = s->charAt(i + 1);
            if (low >= 0xDC00 && low < 0xE000) {
                c = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            }
        }
        if (c < 0x80) {
            out += static_cast<char>(c);
        } else if (c < 0x800) {
            out += static_cast<char>(0xC0 | (c >> 6));
            out += static_cast<char>(0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            out += static_cast<char>(0xE0 | (c >> 12));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (c >> 18));
            out += static_cast<char>(0x80 | ((c >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        }
    }
    return out;
}

/** A Java string from UTF-8 text */
inline ::java::lang::String* javaString(const std::string& text)
{
    std::u16string out;
    for (std::string::size_type i = 0; i < text.size();) {
        unsigned char lead = static_cast<unsigned char>(text[i]);
        int extra = lead < 0x80 ? 0 : lead < 0xE0 ? 1 : lead < 0xF0 ? 2 : 3;
        char32_t c = extra == 0 ? lead : lead & (0x3F >> extra);
        for (int k = 1; k <= extra && i + k < text.size(); ++k) {
            c = (c << 6) | (static_cast<unsigned char>(text[i + k]) & 0x3F);
        }
        i += extra + 1;
        if (c >= 0x10000) {
            out += static_cast<char16_t>(0xD800 + ((c - 0x10000) >> 10));
            out += static_cast<char16_t>(0xDC00 + ((c - 0x10000) & 0x3FF));
        } else {
            out += static_cast<char16_t>(c);
        }
    }
    return j2c_string(out);
}

/** Java's String.valueOf(Object) */
inline std::string valueOf(::java::lang::Object* value)
{
    return value == nullptr ? "null" : utf8(value->toString());
}

/** The class name Java reports, such as java.lang.Integer */
inline std::string className(::java::lang::Object* value)
{
    return utf8(value->getClass()->getName());
}
#endif

/** The Java class name of an exception, such as java.lang.AssertionError */
inline std::string thrownName(::java::lang::Throwable* thrown)
{
#ifdef J2C_JUNIT_RUNTIME
    return className(thrown);
#else
    return javaName(typeid(*thrown));
#endif
}

/** A thrown exception's message, or nullopt when it has none */
inline Text thrownMessage(::java::lang::Throwable* thrown)
{
    if (lastFailure().error == thrown) {
        return lastFailure().message.empty() ? Text() : Text(lastFailure().message);
    }
#ifdef J2C_JUNIT_RUNTIME
    auto message = thrown->getMessage();
    if (message != nullptr) {
        return utf8(message);
    }
#endif
    return Text();
}

/** The JUnit-style message for an exception, or "" */
inline std::string failureMessage(::java::lang::Throwable* error)
{
    return thrownMessage(error).value_or("");
}

/** Throws an AssertionError with this message (nullopt: none) */
[[noreturn]] inline void raise(const Text& message)
{
#ifdef J2C_JUNIT_RUNTIME
    auto error = message ? new ::java::lang::AssertionError(object(javaString(*message)))
                         : new ::java::lang::AssertionError();
#else
    auto error = new ::java::lang::AssertionError();
#endif
    lastFailure().error = error;
    lastFailure().message = message.value_or("");
    throw error;
}

[[noreturn]] inline void fail(const std::string& message)
{
    raise(Text(message));
}

/** The text of a user message, a String or a Supplier<String> */
inline Text messageText()
{
    return Text();
}

inline Text messageText(std::nullptr_t)
{
    return Text();
}

template<typename M>
Text messageText(M* message)
{
#ifdef J2C_JUNIT_RUNTIME
    if (message == nullptr) {
        return Text();
    }
    if constexpr (std::is_convertible_v<M*, ::java::lang::String*>) {
        return utf8(message);
    } else {
        // Supplier<String>, called now that the assertion failed
        return valueOf(message->get());
    }
#else
    (void)message;
    return Text();
#endif
}

/** Java's String.isBlank: empty or only whitespace */
inline bool blank(const std::string& text)
{
    for (char c : text) {
        if (!(c == ' ' || (c >= '\t' && c <= '\r') || (c >= '\x1C' && c <= '\x1F'))) {
            return false;
        }
    }
    return true;
}

/** JUnit 5's message prefix: "message ==> ", or "" for a blank message */
template<typename... Message>
std::string prefix(Message... message)
{
    Text text = messageText(message...);
    return text && !blank(*text) ? *text + " ==> " : "";
}

/** A value as Java's String.valueOf shows it */
template<typename T>
std::string show(T value)
{
    std::ostringstream out;
    if constexpr (std::is_same_v<T, std::nullptr_t>) {
        return "null";
    } else if constexpr (std::is_pointer_v<T>) {
        if (value == nullptr) {
            return "null";
        }
#ifdef J2C_JUNIT_RUNTIME
        return valueOf(object(value));
#else
        out << "an instance of " << javaName(typeid(*value));
#endif
    } else if constexpr (std::is_same_v<T, bool>) {
        out << (value ? "true" : "false");
#ifdef J2C_JUNIT_RUNTIME
    } else if constexpr (std::is_same_v<T, std::int8_t> || std::is_same_v<T, std::int16_t>) {
        return utf8(::java::lang::String::valueOf(static_cast<std::int32_t>(value)));
    } else {
        return utf8(::java::lang::String::valueOf(value));
#else
    } else if constexpr (std::is_same_v<T, char16_t>) {
        out << static_cast<std::uint32_t>(value);
    } else if constexpr (std::is_same_v<T, std::int8_t>) {
        out << static_cast<int>(value);
    } else {
        out << value;
#endif
    }
    return out.str();
}

/** The class of a value as Java would box it, for messages */
template<typename T>
std::string boxedName(T value)
{
    if constexpr (std::is_same_v<T, std::nullptr_t>) {
        return "null";
    } else if constexpr (std::is_pointer_v<T>) {
        if (value == nullptr) {
            return "null";
        }
#ifdef J2C_JUNIT_RUNTIME
        return className(object(value));
#else
        return javaName(typeid(*value));
#endif
    } else if constexpr (std::is_same_v<T, bool>) {
        return "java.lang.Boolean";
    } else if constexpr (std::is_same_v<T, char16_t>) {
        return "java.lang.Character";
    } else if constexpr (std::is_same_v<T, std::int8_t>) {
        return "java.lang.Byte";
    } else if constexpr (std::is_same_v<T, std::int16_t>) {
        return "java.lang.Short";
    } else if constexpr (std::is_same_v<T, std::int32_t>) {
        return "java.lang.Integer";
    } else if constexpr (std::is_same_v<T, std::int64_t>) {
        return "java.lang.Long";
    } else if constexpr (std::is_same_v<T, float>) {
        return "java.lang.Float";
    } else {
        return "java.lang.Double";
    }
}

/** "@" and the identity hash, as JUnit 5 adds after the class name */
template<typename T>
std::string identity(T value)
{
    char text[16];
    std::uint32_t hash = 0;
    if constexpr (std::is_pointer_v<T>) {
#ifdef J2C_JUNIT_RUNTIME
        hash = static_cast<std::uint32_t>(::java::lang::System::identityHashCode(object(value)));
#else
        hash = static_cast<std::uint32_t>(reinterpret_cast<std::uintptr_t>(object(value)) >> 3);
#endif
    }
    std::snprintf(text, sizeof(text), "@%x", hash);
    return text;
}

/** JUnit 5's "expected: <a> but was: <b>", with classes when both read alike */
template<typename E, typename A>
std::string mismatch(E expected, A actual)
{
    std::string e = show(expected);
    std::string a = show(actual);
    if (e != a) {
        return "expected: <" + e + "> but was: <" + a + ">";
    }
    auto classAndValue = [](auto value, const std::string& text) {
        if constexpr (std::is_same_v<decltype(value), std::nullptr_t>) {
            return std::string("<null>");
        } else {
            if constexpr (std::is_pointer_v<decltype(value)>) {
                if (value == nullptr) {
                    return std::string("<null>");
                }
            }
            return boxedName(value) + identity(value) + "<" + text + ">";
        }
    };
    return "expected: " + classAndValue(expected, e) + " but was: " + classAndValue(actual, a);
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

template<typename T>
bool withinDelta(T expected, T actual, T delta)
{
    return floatingEqual(expected, actual) || std::abs(expected - actual) <= delta;
}

template<typename T, typename... Message>
void assertTrue(bool condition, Message... message)
{
    if (!condition) {
        fail(prefix(message...) + "expected: <true> but was: <false>");
    }
}

template<typename T, typename... Message>
void assertFalse(bool condition, Message... message)
{
    if (condition) {
        fail(prefix(message...) + "expected: <false> but was: <true>");
    }
}

template<typename T, typename... Message>
void assertNull(T actual, Message... message)
{
    if (actual != nullptr) {
        fail(prefix(message...) + mismatch(nullptr, actual));
    }
}

template<typename T, typename... Message>
void assertNotNull(T actual, Message... message)
{
    if (actual == nullptr) {
        fail(prefix(message...) + "expected: not <null>");
    }
}

template<typename T, typename... Message>
void assertEquals(T expected, T actual, Message... message)
{
    if (!valuesEqual(expected, actual)) {
        fail(prefix(message...) + mismatch(expected, actual));
    }
}

template<typename T, typename... Message>
void assertNotEquals(T unexpected, T actual, Message... message)
{
    if (valuesEqual(unexpected, actual)) {
        fail(prefix(message...) + "expected: not equal but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertEqualsWithin(T expected, T actual, T delta, Message... message)
{
    if (!withinDelta(expected, actual, delta)) {
        fail(prefix(message...) + mismatch(expected, actual));
    }
}

template<typename T, typename... Message>
void assertNotEqualsWithin(T unexpected, T actual, T delta, Message... message)
{
    if (withinDelta(unexpected, actual, delta)) {
        fail(prefix(message...) + "expected: not equal but was: <" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertSame(T expected, T actual, Message... message)
{
    if (object(expected) != object(actual)) {
        fail(prefix(message...) + mismatch(expected, actual));
    }
}

template<typename T, typename... Message>
void assertNotSame(T unexpected, T actual, Message... message)
{
    if (object(unexpected) == object(actual)) {
        fail(prefix(message...) + "expected: not same but was: <" + show(actual) + ">");
    }
}

template<typename A, typename Compare>
void compareArrays(A expected, A actual, Compare equal, const std::string& prefix)
{
    if (expected == actual) {
        return;
    }
    if (expected == nullptr || actual == nullptr) {
        fail(prefix + (expected == nullptr ? "expected array was <null>" : "actual array was <null>"));
    }
    if (expected->length != actual->length) {
        fail(prefix + "array lengths differ, " + mismatch(expected->length, actual->length));
    }
    for (int i = 0; i < expected->length; ++i) {
        if (!equal(expected->get(i), actual->get(i))) {
            fail(prefix + "array contents differ at index [" + show(i) + "], "
                + mismatch(expected->get(i), actual->get(i)));
        }
    }
}

template<typename A, typename... Message>
void assertArrayEquals(A expected, A actual, Message... message)
{
    compareArrays(expected, actual, [](auto e, auto a) { return valuesEqual(e, a); }, prefix(message...));
}

template<typename A, typename D, typename... Message>
void assertArrayEqualsWithin(A expected, A actual, D delta, Message... message)
{
    compareArrays(expected, actual, [delta](auto e, auto a) { return withinDelta<D>(e, a, delta); },
        prefix(message...));
}

/** fail(), fail(message) and fail(supplier): the message as given */
template<typename T, typename... Message>
[[noreturn]] void failTest(Message... message)
{
    raise(messageText(message...));
}

/** JUnit 4's assertEquals(double, double) without a delta always fails */
template<typename T, typename... Arguments>
[[noreturn]] void floatingWithoutDelta(Arguments&&...)
{
    fail("Use assertEquals(expected, actual, delta) to compare floating-point numbers");
}

/** A class's name as JUnit 5 shows it: the canonical name where there is one */
inline std::string canonicalName(std::string name)
{
    for (std::string::size_type at = name.find('$'); at != std::string::npos; at = name.find('$', at + 1)) {
        if (at + 1 < name.size() && !(name[at + 1] >= '0' && name[at + 1] <= '9')) {
            name[at] = '.';
        }
    }
    return name;
}

/** assertThrows(X.class, executable): returns the exception, as JUnit does */
template<typename Expected, typename E, typename... Message>
Expected* assertThrows(const char* expectedName, E executable, Message... message)
{
    try {
        executable->execute();
    } catch (::java::lang::Throwable* thrown) {
        if (auto expected = dynamic_cast<Expected*>(thrown)) {
            return expected;
        }
        fail(prefix(message...) + "Unexpected exception type thrown, expected: <" + expectedName
            + "> but was: <" + canonicalName(thrownName(thrown)) + ">");
    }
    fail(prefix(message...) + "Expected " + expectedName + " to be thrown, but nothing was thrown.");
}

template<typename E, typename... Message>
void assertDoesNotThrow(E executable, Message... message)
{
    try {
        executable->execute();
    } catch (::java::lang::Throwable* thrown) {
        Text detail = thrownMessage(thrown);
        fail(prefix(message...) + "Unexpected exception thrown: " + thrownName(thrown)
            + (detail && !blank(*detail) ? ": " + *detail : ""));
    }
}

template<typename T, typename... Message>
void assumeTrue(bool condition, Message...)
{
    if (!condition) {
        throw TestAborted();
    }
}

template<typename T, typename... Message>
void assumeFalse(bool condition, Message...)
{
    if (condition) {
        throw TestAborted();
    }
}

/** JUnit 4's org.junit.Assert, whose messages differ from JUnit 5's */
namespace junit4 {

using junit::assumeFalse;
using junit::assumeTrue;
using junit::floatingWithoutDelta;

/** "message " before JUnit 4's own text, or "" without a message */
template<typename... Message>
std::string prefix(Message... message)
{
    Text text = messageText(message...);
    return text ? *text + " " : "";
}

/** Assert.format: "expected:<a> but was:<b>", with classes when both read alike */
template<typename E, typename A>
std::string format(const Text& message, E expected, A actual)
{
    std::string formatted = message && !message->empty() ? *message + " " : "";
    std::string e = show(expected);
    std::string a = show(actual);
    if (e == a) {
        return formatted + "expected: " + boxedName(expected) + "<" + e + "> but was: " + boxedName(actual)
            + "<" + a + ">";
    }
    return formatted + "expected:<" + e + "> but was:<" + a + ">";
}

#ifdef J2C_JUNIT_RUNTIME
/** ComparisonFailure's message: the strings with their difference in brackets */
inline std::string compacted(const Text& message, ::java::lang::String* expected, ::java::lang::String* actual)
{
    const std::string::size_type context = 20;
    std::string e = utf8(expected);
    std::string a = utf8(actual);
    if (e == a) {
        return format(message, expected, actual);
    }
    std::u16string ex;
    std::u16string ac;
    for (int32_t i = 0; i < expected->length(); ++i) {
        ex += expected->charAt(i);
    }
    for (int32_t i = 0; i < actual->length(); ++i) {
        ac += actual->charAt(i);
    }
    std::u16string::size_type end = std::min(ex.size(), ac.size());
    std::u16string::size_type shared = 0;
    while (shared < end && ex[shared] == ac[shared]) {
        ++shared;
    }
    std::u16string::size_type suffix = 0;
    while (suffix + shared < end && ex[ex.size() - 1 - suffix] == ac[ac.size() - 1 - suffix]) {
        ++suffix;
    }
    std::u16string sharedPrefix = ex.substr(0, shared);
    std::u16string sharedSuffix = ex.substr(ex.size() - suffix);
    std::u16string compactPrefix = sharedPrefix.size() <= context
        ? sharedPrefix : u"..." + sharedPrefix.substr(sharedPrefix.size() - context);
    std::u16string compactSuffix = sharedSuffix.size() <= context
        ? sharedSuffix : sharedSuffix.substr(0, context) + u"...";
    auto diff = [&](const std::u16string& source) {
        return j2c_string(compactPrefix + u"[" + source.substr(shared, source.size() - shared - suffix) + u"]"
            + compactSuffix);
    };
    return format(message, object(diff(ex)), object(diff(ac)));
}
#endif

template<typename T, typename... Message>
void assertTrue(bool condition, Message... message)
{
    if (!condition) {
        raise(messageText(message...));
    }
}

template<typename T, typename... Message>
void assertFalse(bool condition, Message... message)
{
    if (condition) {
        raise(messageText(message...));
    }
}

template<typename T, typename... Message>
void assertNull(T actual, Message... message)
{
    if (actual != nullptr) {
        fail(prefix(message...) + "expected null, but was:<" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertNotNull(T actual, Message... message)
{
    if (actual == nullptr) {
        raise(messageText(message...));
    }
}

/** The message of assertEquals(message, expected, actual) */
template<typename T>
std::string notEqual(const Text& message, T expected, T actual)
{
#ifdef J2C_JUNIT_RUNTIME
    if constexpr (std::is_pointer_v<T>) {
        // Two strings throw a ComparisonFailure, which marks the difference
        auto e = dynamic_cast< ::java::lang::String*>(object(expected));
        auto a = dynamic_cast< ::java::lang::String*>(object(actual));
        if (e != nullptr && a != nullptr) {
            return compacted(message ? message : Text(""), e, a);
        }
    }
#endif
    return format(message, expected, actual);
}

template<typename T, typename... Message>
void assertEquals(T expected, T actual, Message... message)
{
    if (!valuesEqual(expected, actual)) {
        fail(notEqual(messageText(message...), expected, actual));
    }
}

/** failEquals: "Values should be different. Actual: x" */
template<typename T, typename... Message>
[[noreturn]] void failEquals(T actual, Message... message)
{
    Text text = messageText(message...);
    fail((text ? *text + ". " : std::string("Values should be different. ")) + "Actual: " + show(actual));
}

template<typename T, typename... Message>
void assertNotEquals(T unexpected, T actual, Message... message)
{
    if (valuesEqual(unexpected, actual)) {
        failEquals(actual, message...);
    }
}

template<typename T, typename... Message>
void assertEqualsWithin(T expected, T actual, T delta, Message... message)
{
    if (!withinDelta(expected, actual, delta)) {
        fail(format(messageText(message...), expected, actual));
    }
}

template<typename T, typename... Message>
void assertNotEqualsWithin(T unexpected, T actual, T delta, Message... message)
{
    if (withinDelta(unexpected, actual, delta)) {
        failEquals(actual, message...);
    }
}

template<typename T, typename... Message>
void assertSame(T expected, T actual, Message... message)
{
    if (object(expected) != object(actual)) {
        fail(prefix(message...) + "expected same:<" + show(expected) + "> was not:<" + show(actual) + ">");
    }
}

template<typename T, typename... Message>
void assertNotSame(T unexpected, T actual, Message... message)
{
    if (object(unexpected) == object(actual)) {
        fail(prefix(message...) + "expected not same");
    }
}

/**
 * ComparisonCriteria.arrayEquals: elements compared as assertEquals does,
 * then "end of array" where one array is longer
 */
template<typename A, typename Equal, typename Describe>
void compareArrays(A expected, A actual, Equal equal, Describe describe, const Text& message)
{
    if (expected == actual) {
        return;
    }
    std::string header = message ? *message + ": " : "";
    if (expected == nullptr || actual == nullptr) {
        fail(header + (expected == nullptr ? "expected array was null" : "actual array was null"));
    }
    if (expected->length != actual->length) {
        header += "array lengths differed, expected.length=" + show(expected->length)
            + " actual.length=" + show(actual->length) + "; ";
    }
    int shared = std::min(expected->length, actual->length);
    for (int i = 0; i < shared; ++i) {
        if (!equal(expected->get(i), actual->get(i))) {
            fail(header + "arrays first differed at element [" + show(i) + "]; "
                + describe(expected->get(i), actual->get(i)));
        }
    }
    if (expected->length != actual->length) {
        std::string e = shared < expected->length ? show(expected->get(shared)) : "end of array";
        std::string a = shared < actual->length ? show(actual->get(shared)) : "end of array";
        fail(header + "arrays first differed at element [" + show(shared) + "]; expected:<" + e
            + "> but was:<" + a + ">");
    }
}

template<typename A, typename... Message>
void assertArrayEquals(A expected, A actual, Message... message)
{
    compareArrays(expected, actual, [](auto e, auto a) { return valuesEqual(e, a); },
        [](auto e, auto a) { return notEqual(Text(), e, a); }, messageText(message...));
}

template<typename A, typename D, typename... Message>
void assertArrayEqualsWithin(A expected, A actual, D delta, Message... message)
{
    compareArrays(expected, actual, [delta](auto e, auto a) { return withinDelta<D>(e, a, delta); },
        [](auto e, auto a) { return format(Text(), e, a); }, messageText(message...));
}

template<typename T, typename... Message>
[[noreturn]] void failTest(Message... message)
{
    raise(messageText(message...));
}

}

}
}
