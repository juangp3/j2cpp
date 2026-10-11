// Hooks that generated code calls, and helpers for the natives
#include <charconv>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>

#include <Array.hpp>
#include <SubArray.hpp>
#include <java/io/Serializable.hpp>
#include <java/lang/Class.hpp>
#include <java/lang/String.hpp>
#include <java/lang/System.hpp>
#include <java/lang/Throwable.hpp>

#include "j2c_runtime.hpp"

java::lang::String* j2c_string(const std::u16string& text)
{
    return new java::lang::String(new ::char16_tArray(text.data(), static_cast<int>(text.size())));
}

std::string j2c_ascii(java::lang::String* s)
{
    std::string result;
    for (int32_t i = 0; i < s->length(); i++) {
        result += static_cast<char>(s->charAt(i));
    }
    return result;
}

std::u16string j2c_floating_text(double value, bool isFloat)
{
    if (std::isnan(value)) {
        return u"NaN";
    }
    if (std::isinf(value)) {
        return value > 0 ? u"Infinity" : u"-Infinity";
    }
    if (value == 0) {
        return std::signbit(value) ? u"-0.0" : u"0.0";
    }

    // The shortest digits that round-trip, as "d.ddde[+-]x"
    char buf[64];
    std::to_chars_result r = isFloat
        ? std::to_chars(buf, buf + sizeof(buf), static_cast<float>(value), std::chars_format::scientific)
        : std::to_chars(buf, buf + sizeof(buf), value, std::chars_format::scientific);
    std::string text(buf, r.ptr);
    std::u16string out;
    size_t pos = 0;
    if (text[0] == '-') {
        out += u'-';
        pos = 1;
    }
    size_t e = text.find('e');
    std::string digits;
    for (size_t i = pos; i < e; i++) {
        if (text[i] != '.') {
            digits += text[i];
        }
    }
    int exponent = std::atoi(text.c_str() + e + 1);

    double magnitude = std::fabs(value);
    if (magnitude >= 1e-3 && magnitude < 1e7) {
        // Plain decimal, with at least one digit after the point
        std::string whole;
        std::string fraction;
        if (exponent >= 0) {
            for (int i = 0; i <= exponent; i++) {
                whole += i < static_cast<int>(digits.size()) ? digits[i] : '0';
            }
            if (exponent + 1 < static_cast<int>(digits.size())) {
                fraction = digits.substr(exponent + 1);
            }
        } else {
            whole = "0";
            fraction = std::string(-exponent - 1, '0') + digits;
        }
        if (fraction.empty()) {
            fraction = "0";
        }
        for (char c : whole) {
            out += static_cast<char16_t>(c);
        }
        out += u'.';
        for (char c : fraction) {
            out += static_cast<char16_t>(c);
        }
        return out;
    }

    // Computerized scientific notation: d.dddE[-]x
    out += static_cast<char16_t>(digits[0]);
    out += u'.';
    if (digits.size() > 1) {
        for (size_t i = 1; i < digits.size(); i++) {
            out += static_cast<char16_t>(digits[i]);
        }
    } else {
        out += u'0';
    }
    out += u'E';
    for (char c : std::to_string(exponent)) {
        out += static_cast<char16_t>(c);
    }
    return out;
}

::java::lang::Class* class_(const char16_t* name, int length)
{
    // One Class per name, so getClass() results compare with ==
    static std::unordered_map<std::u16string, ::java::lang::Class*> classes;
    std::u16string key(name, length);
    auto found = classes.find(key);
    if (found != classes.end()) {
        return found->second;
    }
    auto cls = ::java::lang::Class::j2cForName(j2c_string(key));
    classes.emplace(key, cls);
    return cls;
}

void unimplemented_(const char16_t* name)
{
    std::fputs("j2c: call to an unimplemented Java library method: ", stderr);
    for (; *name; ++name) {
        std::fputc(*name < 0x80 ? static_cast<char>(*name) : '?', stderr);
    }
    std::fputc('\n', stderr);
    std::abort();
}

// Literals are interned, so equal literals are the same object, as in Java
java::lang::String* java::lang::operator "" _j(const char16_t* p, size_t n)
{
    return (new String(new ::char16_tArray(p, static_cast<int>(n))))->intern();
}

void init_jvm()
{
}

// main's String[] from the C arguments; bytes are taken as Latin-1
::java::lang::StringArray* make_args(int argc, char** argv)
{
    auto args = java::lang::System::j2cArgs(argc > 0 ? argc - 1 : 0);
    for (int i = 1; i < argc; i++) {
        int n = static_cast<int>(std::strlen(argv[i]));
        auto chars = new ::char16_tArray(n);
        for (int j = 0; j < n; j++) {
            (*chars)[j] = static_cast<unsigned char>(argv[i][j]);
        }
        args->set(i - 1, new java::lang::String(chars));
    }
    return args;
}

void j2c_uncaught(java::lang::Throwable* t)
{
    java::lang::System::j2cUncaught(t);
}
