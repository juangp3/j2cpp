#include <se/arnetheduck/j2c/test/Java17ControlFlow.hpp>

using se::arnetheduck::j2c::test::Java17ControlFlow;

// These hooks isolate switch-expression behavior from the translated runtime.
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
	return 0;
}
