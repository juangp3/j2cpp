// Helpers shared by the runtime's natives (runtime/cpp/native)
#pragma once

#include <string>

namespace java {
namespace lang {
class String;
}
}

// A String holding a copy of the UTF-16 text
java::lang::String* j2c_string(const std::u16string& text);

// Java's Double.toString or Float.toString text for a value
std::u16string j2c_floating_text(double value, bool isFloat);

// The ASCII characters of a String (callers pass validated, ASCII text)
std::string j2c_ascii(java::lang::String* s);
