#pragma once

#include <initializer_list>
#include <type_traits>

#include <java/lang/Object.hpp>
#include <java/lang/Cloneable.hpp>
#include <java/io/Serializable.hpp>

namespace java
{
namespace lang
{
class ObjectArray;
}
}

extern java::lang::Class *class_(const char16_t *c, int n);

#ifndef J2C_ARRAY_CHECKS
#define J2C_ARRAY_CHECKS
// Java's array checks. The throwing helpers are defined in j2c_array.cpp,
// so these headers need no exception class. Compile with
// -DJ2C_NO_BOUNDS_CHECKS to index without checking (C++ undefined behavior
// instead of ArrayIndexOutOfBoundsException); negative sizes are always
// checked, since allocation is never a hot path.
[[noreturn, gnu::cold]] void j2c_throw_array_index(int index, int length);
[[noreturn, gnu::cold]] void j2c_throw_negative_array_size(int size);

inline int j2c_check_index(int index, int length)
{
#ifndef J2C_NO_BOUNDS_CHECKS
    // One unsigned compare also catches negative indices
    if(static_cast<unsigned>(index) >= static_cast<unsigned>(length)) [[unlikely]] {
        j2c_throw_array_index(index, length);
    }
#endif
    return index;
}

inline int j2c_check_size(int size)
{
    if(size < 0) [[unlikely]] {
        j2c_throw_negative_array_size(size);
    }
    return size;
}
#endif

class java::lang::ObjectArray
    : public virtual ::java::lang::Object
    , public virtual ::java::lang::Cloneable
    , public virtual ::java::io::Serializable
{
public:
    static ::java::lang::Class *class_() {
        static ::java::lang::Class* c = ::class_(u"[Ljava.lang.Object;", 19);
        return c;
    }

    typedef ::java::lang::Object super;

    typedef Object *value_type;
    typedef value_type *pointer_type;
    typedef pointer_type iterator;
    typedef const value_type *const_pointer_type;
    typedef const_pointer_type const_iterator;

    typedef int size_type;

    ObjectArray() : length(0), p(nullptr) { }
    ObjectArray(int n) : length(j2c_check_size(n)), p(n == 0 ? nullptr : new value_type[n])
    {
    	for(auto x = p; x != p + length; ++x) *x = value_type();
    }

    ObjectArray(const value_type *values, int n) : length(n), p(new value_type[n])
    {
    	auto x = p;
    	for(auto v = values; v != values + n; ++v) *x++ = *v;
    }

    template<typename T>
    ObjectArray(std::initializer_list<T> l) : length(l.size()), p(new value_type[l.size()])
    {
    	auto x = p;
    	for(auto v : l) *x++ = v;
    }

    ObjectArray(const ObjectArray &rhs) : ObjectArray(rhs.p, rhs.length)
    {
    }

    ObjectArray(ObjectArray &&rhs) : length(rhs.length), p(rhs.p)
    {
    	const_cast<pointer_type&>(rhs.p) = 0;
    }

    ObjectArray &operator=(const ObjectArray &rhs)
    {
        if(&rhs != this) {
            if(length != rhs.length) {
                delete p;
                const_cast<pointer_type&>(p) = 0;
                const_cast<size_type&>(length) = rhs.length;
                const_cast<pointer_type&>(p) = new value_type[length];
            }

            auto x = p;
            for(auto v = rhs.p; v != rhs.p + rhs.length; ++v) *x++ = *v;
        }

        return *this;
    }

    ObjectArray &operator=(ObjectArray &&rhs)
    {
        if(&rhs != this) {
            delete p;
            const_cast<size_type&>(length) = rhs.length;
            const_cast<pointer_type&>(p) = rhs.p;
            const_cast<pointer_type&>(rhs.p) = 0;
        }

        return *this;
    }

    virtual ~ObjectArray() { delete p; }

    ObjectArray* clone() override { return new ObjectArray(*this); }
    // A new array of n nulls with this array's runtime type (toArray(T[]))
    virtual ObjectArray* newInstance0(int n) { return new ObjectArray(n); }

    value_type operator[](size_type i) const { return get(i); }
    value_type get(size_type i) const { return p[j2c_check_index(i, length)]; }

    template<typename T>
    T set(size_type i, T x) { set0(j2c_check_index(i, length), x); return x; }

    iterator        begin() { return p; }
    const_iterator  begin() const { return p; }
    const_iterator  cbegin() const { return begin(); }

    iterator        end() { return p + length; }
    const_iterator  end() const { return p + length; }
    const_iterator  cend() const { return end(); }

    const size_type length;
    const pointer_type p;

private:
    ::java::lang::Class *getClass0() override { return class_(); }

     virtual void set0(size_type i, Object *x) { p[i] = x; }
};

template<typename ArrayType>
ArrayType* __newMultiArray(int dim) {
     return new ArrayType(dim);
}

template<typename ArrayType, class... Dims>
ArrayType* __newMultiArray(int dim, Dims... dims) {
    // Java checks every dimension before allocating, even below a 0
    j2c_check_size(dim);
    (j2c_check_size(dims), ...);
    auto ret = new ArrayType(dim);
    for (auto i = 0; i < dim; ++i) {
        ret->p[i] = __newMultiArray<typename std::remove_pointer<typename ArrayType::value_type>::type>(dims...);
    }
    return ret;
}
