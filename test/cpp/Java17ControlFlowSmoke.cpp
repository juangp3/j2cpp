#include <se/arnetheduck/j2c/test/Java17ControlFlow.hpp>

using se::arnetheduck::j2c::test::Java17ControlFlow;

// These hooks isolate switch behavior from the translated runtime.
::java::lang::Class* class_(const char16_t*, int) {
	return nullptr;
}

void ::java::lang::Object::clinit() {
}

int main() {
	if (Java17ControlFlow::switchValue(0) != 1) return 1;
	if (Java17ControlFlow::switchValue(1) != 3) return 2;
	if (Java17ControlFlow::switchValue(2) != 3) return 3;
	if (Java17ControlFlow::switchValue(9) != 4) return 4;
	// Arrow rules in switch statements must neither return nor fall through
	if (Java17ControlFlow::switchRule(0) != 11) return 5;
	if (Java17ControlFlow::switchRule(1) != 21) return 6;
	if (Java17ControlFlow::switchRule(2) != 21) return 7;
	if (Java17ControlFlow::switchRule(9) != 31) return 8;
	return 0;
}
