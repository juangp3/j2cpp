#include <cmath>
#include <cstdint>
#include <limits>

#include <Array.hpp>
#include <java/lang/ArithmeticException.hpp>
#include <java/lang/String.hpp>
#include <se/arnetheduck/j2c/test/Java17Arithmetic.hpp>

using se::arnetheduck::j2c::test::Java17Arithmetic;

// Runtime hooks: the generated Java library is stubbed.
::java::lang::Class* class_(const char16_t*, int) {
	return nullptr;
}

void unimplemented_(const char16_t*) {
}

java::lang::String* java::lang::operator "" _j(const char16_t*, size_t) {
	return nullptr;
}

static bool divisionByZeroThrows(int which) {
	try {
		switch (which) {
		case 0: Java17Arithmetic::div(1, 0); break;
		case 1: Java17Arithmetic::rem(1, 0); break;
		case 2: Java17Arithmetic::divLong(1, 0); break;
		default: Java17Arithmetic::divAssign(1, 0); break;
		}
	} catch (::java::lang::ArithmeticException*) {
		return true;
	}
	return false;
}

int main() {
	const int32_t intMin = std::numeric_limits<int32_t>::min();
	const int32_t intMax = std::numeric_limits<int32_t>::max();
	const int64_t longMin = std::numeric_limits<int64_t>::min();
	const int64_t longMax = std::numeric_limits<int64_t>::max();

	for (int which = 0; which < 4; ++which) {
		if (!divisionByZeroThrows(which)) return 1;
	}
	if (Java17Arithmetic::div(intMin, -1) != intMin) return 2;
	if (Java17Arithmetic::rem(intMin, -1) != 0) return 3;
	if (Java17Arithmetic::divLong(longMin, -1) != longMin) return 4;
	if (Java17Arithmetic::div(-7, 2) != -3 || Java17Arithmetic::rem(-7, 2) != -1) return 5;
	if (Java17Arithmetic::chain(7, 2, 2) != 1) return 6;
	if (Java17Arithmetic::half(-7) != -3) return 7;
	if (Java17Arithmetic::add(intMax, 1) != intMin) return 8;

	if (Java17Arithmetic::shl(1, 33) != 2) return 9;
	if (Java17Arithmetic::shl(1, -1) != intMin) return 10;
	if (Java17Arithmetic::shlLong(1, 65) != 2) return 11;
	if (Java17Arithmetic::shr(-8, 33) != -4) return 12;
	if (Java17Arithmetic::ushr(-1, 28) != 15) return 13;
	if (Java17Arithmetic::ushr(-1, 60) != 15) return 14;

	if (Java17Arithmetic::d2i(NAN) != 0) return 15;
	if (Java17Arithmetic::d2i(1e20) != intMax) return 16;
	if (Java17Arithmetic::d2i(-1e20) != intMin) return 17;
	if (Java17Arithmetic::d2i(-2.9) != -2) return 18;
	if (Java17Arithmetic::d2l(1e30) != longMax) return 19;
	if (Java17Arithmetic::f2i(INFINITY) != intMax) return 20;
	// (byte) 300.7 is (byte) (int) 300.7 == 44
	if (Java17Arithmetic::d2b(300.7) != 44) return 21;

	if (Java17Arithmetic::divAssign(intMin, -1) != intMin) return 22;
	if (Java17Arithmetic::remAssign(7, -3) != 1) return 23;
	if (Java17Arithmetic::shlAssign(1, 33) != 2) return 24;
	if (Java17Arithmetic::ushrAssign(-1, 60) != 15) return 25;
	if (Java17Arithmetic::byteDivAssign(-128, -1) != -128) return 26;

	auto values = new ::int32_tArray(2);
	(*values)[0] = 10;
	if (Java17Arithmetic::compoundIndex(values, 5) != 1 || (*values)[0] != 2) return 27;
	if (Java17Arithmetic::fmodAssign(7.5, 2.0) != 1.5) return 28;
	return 0;
}
