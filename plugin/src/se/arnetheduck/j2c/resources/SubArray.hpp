#pragma once

#include <iterator>
#include <utility>

#include <ObjectArray.hpp>
#include <java/lang/ArrayStoreException.hpp>

#ifndef J2C_ARRAY_NAME
#define J2C_ARRAY_NAME
// The binary name of an array class ("[Lp.Outer$Inner;"), as a template
// argument, so every header's typedef of an array names the same type
template<int N> struct j2c_array_name {
    char16_t s[N];
    constexpr j2c_array_name(const char16_t (&p)[N]) { for (int i = 0; i < N; ++i) s[i] = p[i]; }
};
#endif

template<j2c_array_name Name, typename ComponentType, typename... Bases>
struct SubArray : public virtual Bases... {
    static ::java::lang::Class *class_() {
        static ::java::lang::Class* c = ::class_(Name.s, sizeof(Name.s) / sizeof(char16_t) - 1);
        return c;
    }

    typedef ComponentType* value_type;
    typedef int            size_type;

    struct iterator {
        typedef SubArray::value_type    value_type;
        typedef std::ptrdiff_t          difference_type;
        typedef value_type*             pointer;
        typedef value_type              reference; // Good enough for input iterator
        typedef std::input_iterator_tag iterator_category;

        iterator() : p() {}
        explicit iterator(::java::lang::Object** p) : p(p) {}

        pointer     operator->()    { return &dynamic_cast<value_type>(*p); }
        reference   operator*()     { return dynamic_cast<value_type>(*p); }

        iterator&   operator++()    { ++p; return *this; }
        iterator    operator++(int) { iterator tmp(p); ++*this; return tmp; }
        iterator&   operator--()    { --p; return *this; }
        iterator    operator--(int) { iterator tmp(p); --*this; return tmp; }

        bool operator==(iterator rhs) { return p == rhs.p; }
        bool operator!=(iterator rhs) { return !(*this == rhs); }

        ::java::lang::Object**  p;
    };

    SubArray() { }
    SubArray(int n) : ::java::lang::ObjectArray(n) { }

    SubArray(const value_type *values, int n) : ::java::lang::ObjectArray(n)
    {
    	auto x = this->p;
    	for(auto v = values; v != values + n; ++v) *x++ = *v;
    }

    template<typename T>
    SubArray(std::initializer_list<T> l) : ::java::lang::ObjectArray(l.size())
    {
    	auto x = this->p;
    	for(auto v : l) *x++ = v;
    }

    SubArray(const SubArray &rhs) : ::java::lang::ObjectArray(rhs) { }
    SubArray(SubArray &&rhs) : ::java::lang::ObjectArray(std::move(rhs)) { }
    virtual ~SubArray() {}

    SubArray &operator=(const SubArray &rhs)
    {
        ::java::lang::ObjectArray::operator=(rhs);
        return *this;
    }

    SubArray &operator=(SubArray &&rhs)
    {
        ::java::lang::ObjectArray::operator=(std::move(rhs));
        return *this;
    }

    SubArray* clone() override { return new SubArray(*this); }
    SubArray* newInstance0(int n) override { return new SubArray(n); }

    value_type operator[](size_type i) const { return get(i); }
    value_type get(size_type i) const { return dynamic_cast<value_type>(this->p[i]); }

    iterator   begin() { return iterator(this->p); }
    iterator   end() { return iterator(this->p + this->length); }

private:	
    ::java::lang::Class *getClass0() override { return class_(); }

    void set0(size_type i, ::java::lang::Object *x) override
    {
        if(x && !dynamic_cast<value_type>(x)) {
            throw new ::java::lang::ArrayStoreException();
        }
        
        this->p[i] = x;
    }
};
