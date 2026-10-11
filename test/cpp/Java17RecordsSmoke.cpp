#include <java/lang/String.hpp>
#include <se/arnetheduck/j2c/test/Java17Records.hpp>

using se::arnetheduck::j2c::test::Java17Records;

// Runtime hooks: the generated Java library is stubbed.
::java::lang::Class* class_(const char16_t*, int) {
	return nullptr;
}

void unimplemented_(const char16_t*) {
}

java::lang::String* java::lang::operator "" _j(const char16_t*, size_t) {
	return nullptr;
}

int main() {
	// Records implementing a sealed interface, matched with patterns
	if (Java17Records::sum() != 9) return 1;
	// Enum constants with bodies, built through overloaded enum constructors
	if (Java17Records::tierScore() != 2278) return 50;
	return 0;
}
