// Runtime hooks for test binaries built from stubbed JDK classes: the
// generated code calls these, and a Java runtime would normally provide them.
// Link with TEST_LDLIBS when running the generated GoogleTest wrappers.
#include <cstddef>

namespace java { namespace lang { class Class; } }

::java::lang::Class* class_(const char16_t*, int) {
	return nullptr;
}

void unimplemented_(const char16_t*) {
}

// Only projects with string literals declare (and use) this hook
#if __has_include(<java/lang/String.hpp>)
#include <java/lang/String.hpp>

java::lang::String* java::lang::operator "" _j(const char16_t*, size_t) {
	return nullptr;
}
#endif
