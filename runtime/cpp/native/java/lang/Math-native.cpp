// Natives of java.lang.Math, from <cmath>
#include <cmath>

#include <java/lang/Math.hpp>

double java::lang::Math::sqrt(double a)
{
    clinit();
    return std::sqrt(a);
}

double java::lang::Math::pow(double a, double b)
{
    clinit();
    return std::pow(a, b);
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
