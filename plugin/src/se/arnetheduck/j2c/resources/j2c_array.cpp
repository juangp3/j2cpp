// The exceptions of Java's array checks (Array.hpp, ObjectArray.hpp), with
// the JVM's messages
#include <string>

#include <Array.hpp>
#include <java/lang/ArrayIndexOutOfBoundsException.hpp>
#include <java/lang/NegativeArraySizeException.hpp>
#include <java/lang/String.hpp>

namespace java { namespace lang { String* operator "" _j(const char16_t* p, size_t n); } }

static void j2c_append(std::u16string &s, int value)
{
    for(char c : std::to_string(value)) {
        s += static_cast<char16_t>(c);
    }
}

void j2c_throw_array_index(int index, int length)
{
    std::u16string s(u"Index ");
    j2c_append(s, index);
    s += u" out of bounds for length ";
    j2c_append(s, length);
    throw new ::java::lang::ArrayIndexOutOfBoundsException(
        ::java::lang::operator "" _j(s.data(), s.size()));
}

void j2c_throw_negative_array_size(int size)
{
    std::u16string s;
    j2c_append(s, size);
    throw new ::java::lang::NegativeArraySizeException(
        ::java::lang::operator "" _j(s.data(), s.size()));
}
