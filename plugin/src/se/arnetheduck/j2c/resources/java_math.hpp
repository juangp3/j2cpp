// Java integer arithmetic, where C++ leaves the result undefined: division
// by zero throws, MIN_VALUE / -1 wraps, shift counts are masked to the type
// width, and floating-point to integer casts saturate (NaN becomes 0).
template<typename T, typename U>
static T jdiv(T a, U b)
{
    if(b == 0) throw new ::java::lang::ArithmeticException(u"/ by zero"_j);
    if(b == -1) return static_cast<T>(-static_cast<std::make_unsigned_t<T>>(a));
    return a / static_cast<T>(b);
}

template<typename T, typename U>
static T jrem(T a, U b)
{
    if(b == 0) throw new ::java::lang::ArithmeticException(u"/ by zero"_j);
    if(b == -1) return 0;
    return a % static_cast<T>(b);
}

template<typename T>
static T jshl(T a, int64_t s)
{
    return static_cast<T>(static_cast<std::make_unsigned_t<T>>(a) << (s & (sizeof(T) * 8 - 1)));
}

template<typename T>
static T jshr(T a, int64_t s)
{
    return a >> (s & (sizeof(T) * 8 - 1));
}

template<typename T>
static T jushr(T a, int64_t s)
{
    return static_cast<T>(static_cast<std::make_unsigned_t<T>>(a) >> (s & (sizeof(T) * 8 - 1)));
}

template<typename T, typename F>
static T jf2i(F f)
{
    if(f != f) return 0;
    if(f >= static_cast<F>(std::numeric_limits<T>::max())) return std::numeric_limits<T>::max();
    if(f <= static_cast<F>(std::numeric_limits<T>::min())) return std::numeric_limits<T>::min();
    return static_cast<T>(f);
}

// Compound assignments evaluate their target once; P is the promoted type
template<typename P, typename T, typename U>
static T& jdiv_set(T& a, U b) { return a = static_cast<T>(jdiv<P>(static_cast<P>(a), b)); }

template<typename P, typename T, typename U>
static T& jrem_set(T& a, U b) { return a = static_cast<T>(jrem<P>(static_cast<P>(a), b)); }

template<typename P, typename T>
static T& jshl_set(T& a, int64_t s) { return a = static_cast<T>(jshl<P>(static_cast<P>(a), s)); }

template<typename P, typename T>
static T& jshr_set(T& a, int64_t s) { return a = static_cast<T>(jshr<P>(static_cast<P>(a), s)); }

template<typename P, typename T>
static T& jushr_set(T& a, int64_t s) { return a = static_cast<T>(jushr<P>(static_cast<P>(a), s)); }
