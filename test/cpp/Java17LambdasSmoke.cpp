#include <Array.hpp>
#include <java/lang/Runnable.hpp>
#include <java/lang/String.hpp>
#include <se/arnetheduck/j2c/test/Java17Lambdas.hpp>

using se::arnetheduck::j2c::test::Java17Lambdas;

// Runtime hooks: the generated Java library is stubbed, so only lambdas that
// need no real library code are exercised here.
::java::lang::Class* class_(const char16_t*, int) {
	return nullptr;
}

void unimplemented_(const char16_t*) {
}

java::lang::String* java::lang::operator "" _j(const char16_t*, size_t) {
	return nullptr;
}

int main() {
	auto lambdas = new Java17Lambdas();
	// (a, b) -> a * b + base, with base == 10 read through the captured this
	if (lambdas->expressionBody() != 16) return 1;
	// Block body capturing the effectively final parameter offset
	if (lambdas->blockBody(5) != 8) return 2;
	if (lambdas->blockBody(-3) != 0) return 3;
	// int[]::new
	if (Java17Lambdas::arrayConstructorReference()->length != 3) return 4;
	// (Runnable & Serializable) () -> counter[0]++
	auto counter = new ::int32_tArray(1);
	auto task = Java17Lambdas::serializableTask(counter);
	task->run();
	task->run();
	if ((*counter)[0] != 2) return 5;
	if (!Java17Lambdas::isSerializable(task)) return 6;
	if (Java17Lambdas::genericReference() == nullptr) return 7;
	return 0;
}
