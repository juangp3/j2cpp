#include <exception>

namespace
{
template<typename F>
struct finally_
{
    finally_(F f, bool unwind) : f(f), moved(false), unwind(unwind), uncaught(std::uncaught_exceptions()) { }
    finally_(finally_ &&x) : f(x.f), moved(false), unwind(x.unwind), uncaught(x.uncaught) { x.moved = true; }
    // Java code run on exit may throw, which C++ allows on a normal exit only
    ~finally_() noexcept(false) { if(!moved && (unwind || std::uncaught_exceptions() == uncaught)) f(); }
private:
    finally_(const finally_&); finally_& operator=(const finally_&);
    F f;
    bool moved;
    bool unwind;
    int uncaught;
};

template<typename F> finally_<F> finally(F f) { return finally_<F>(f, true); }

// Runs f on a normal exit only, for code that handles exceptions itself
template<typename F> finally_<F> on_normal_exit(F f) { return finally_<F>(f, false); }
}
