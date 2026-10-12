// Natives of java.lang.Math, from <cmath>
#include <cmath>

#include <java/lang/Math.hpp>

int64_t java::lang::Math::multiplyHigh(int64_t x, int64_t y)
{
    clinit();
    return static_cast<int64_t>((static_cast<__int128>(x) * y) >> 64);
}

double java::lang::Math::floor(double a)
{
    clinit();
    return std::floor(a);
}

double java::lang::Math::ceil(double a)
{
    clinit();
    return std::ceil(a);
}

// The default rounding mode rounds ties to even, as Java's rint does
double java::lang::Math::rint(double a)
{
    clinit();
    return std::nearbyint(a);
}

double java::lang::Math::nextAfter(double start, double direction)
{
    clinit();
    return std::nextafter(start, direction);
}

float java::lang::Math::nextAfter(float start, double direction)
{
    clinit();
    if (std::isnan(start) || std::isnan(direction)) {
        return start + static_cast<float>(direction);
    }
    if (static_cast<double>(start) == direction) {
        return static_cast<float>(direction);
    }
    return std::nextafter(start, static_cast<double>(start) < direction ? INFINITY : -INFINITY);
}

double java::lang::Math::scalb(double d, int32_t scaleFactor)
{
    clinit();
    return std::scalbn(d, scaleFactor);
}

float java::lang::Math::scalb(float f, int32_t scaleFactor)
{
    clinit();
    return std::scalbn(f, scaleFactor);
}

double java::lang::Math::sqrt(double a)
{
    clinit();
    return std::sqrt(a);
}

// cbrt in extended precision, so perfect cubes come out exact (glibc's
// double cbrt(27.0) is 3.0000000000000004)
double java::lang::Math::cbrt(double a)
{
    clinit();
    return static_cast<double>(std::cbrt(static_cast<long double>(a)));
}

double java::lang::Math::pow(double a, double b)
{
    clinit();
    // C returns 1 for pow(1, NaN) and pow(-1, +-Infinity); Java returns NaN
    if (std::isnan(b) || (std::isinf(b) && std::fabs(a) == 1.0)) {
        return NAN;
    }
    return std::pow(a, b);
}

double java::lang::Math::hypot(double x, double y)
{
    clinit();
    return std::hypot(x, y);
}

double java::lang::Math::exp(double a)
{
    clinit();
    return std::exp(a);
}

double java::lang::Math::expm1(double x)
{
    clinit();
    return std::expm1(x);
}

double java::lang::Math::log(double a)
{
    clinit();
    return std::log(a);
}

double java::lang::Math::log10(double a)
{
    clinit();
    return std::log10(a);
}

double java::lang::Math::log1p(double x)
{
    clinit();
    return std::log1p(x);
}

double java::lang::Math::IEEEremainder(double f1, double f2)
{
    clinit();
    return std::remainder(f1, f2);
}

double java::lang::Math::fma(double a, double b, double c)
{
    clinit();
    return std::fma(a, b, c);
}

float java::lang::Math::fma(float a, float b, float c)
{
    clinit();
    return std::fma(a, b, c);
}

double java::lang::Math::sin(double a)
{
    clinit();
    return std::sin(a);
}

double java::lang::Math::cos(double a)
{
    clinit();
    return std::cos(a);
}

double java::lang::Math::tan(double a)
{
    clinit();
    return std::tan(a);
}

double java::lang::Math::asin(double a)
{
    clinit();
    return std::asin(a);
}

double java::lang::Math::acos(double a)
{
    clinit();
    return std::acos(a);
}

double java::lang::Math::atan(double a)
{
    clinit();
    return std::atan(a);
}

double java::lang::Math::atan2(double y, double x)
{
    clinit();
    return std::atan2(y, x);
}

double java::lang::Math::sinh(double x)
{
    clinit();
    return std::sinh(x);
}

double java::lang::Math::cosh(double x)
{
    clinit();
    return std::cosh(x);
}

double java::lang::Math::tanh(double x)
{
    clinit();
    return std::tanh(x);
}
