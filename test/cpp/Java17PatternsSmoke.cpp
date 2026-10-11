#include <se/arnetheduck/j2c/test/Java17Patterns.hpp>
#include <se/arnetheduck/j2c/test/Java17Patterns_Circle.hpp>
#include <se/arnetheduck/j2c/test/Java17Patterns_Counter.hpp>
#include <se/arnetheduck/j2c/test/Java17Patterns_Square.hpp>

using namespace se::arnetheduck::j2c::test;

// Runtime hooks: the generated Java library is stubbed; these tests only use
// the fixture's own classes.
::java::lang::Class* class_(const char16_t*, int) {
	return nullptr;
}

void unimplemented_(const char16_t*) {
}

int main() {
	auto small = new Java17Patterns_Circle(1);
	auto circle = new Java17Patterns_Circle(5);
	auto square = new Java17Patterns_Square(3);

	if (Java17Patterns::and_(circle) != 5 || Java17Patterns::and_(small) != 0
			|| Java17Patterns::and_(square) != 0) return 1;
	if (Java17Patterns::negated(square) != 3 || Java17Patterns::negated(circle) != -1) return 2;
	if (Java17Patterns::loop(new Java17Patterns_Counter(4)) != 4) return 3;
	if (Java17Patterns::ternary(circle) != 5 || Java17Patterns::ternary(square) != 30
			|| Java17Patterns::ternary(nullptr) != -1) return 4;
	if (Java17Patterns::siblings(circle, small) != 105) return 5;
	if (Java17Patterns::elseIf(square) != 3 || Java17Patterns::elseIf(nullptr) != -2
			|| Java17Patterns::elseIf(circle) != 0) return 6;
	if (Java17Patterns::unbraced(circle, true) != 5 || Java17Patterns::unbraced(circle, false) != -1) return 7;
	if (Java17Patterns::inSwitch(1, circle) != 5 || Java17Patterns::inSwitch(1, square) != -1) return 8;
	if (Java17Patterns::nullOperand() != 7) return 9;
	if (Java17Patterns::thirdOperand(true, true, circle) != 5 || Java17Patterns::thirdOperand(true, false, circle) != -1
			|| Java17Patterns::thirdOperand(true, true, square) != -1) return 10;
	return 0;
}
