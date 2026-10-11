// Hooks that generated code calls (see docs/RUNTIME_EXPERIMENT.md)
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <Array.hpp>
#include <SubArray.hpp>
#include <java/io/Serializable.hpp>
#include <java/lang/String.hpp>
#include <java/lang/System.hpp>

::java::lang::Class* class_(const char16_t*, int)
{
    // No reflection yet
    return nullptr;
}

void unimplemented_(const char16_t* name)
{
    std::fputs("j2c: call to an unimplemented Java library method: ", stderr);
    for (; *name; ++name) {
        std::fputc(*name < 0x80 ? static_cast<char>(*name) : '?', stderr);
    }
    std::fputc('\n', stderr);
    std::abort();
}

java::lang::String* java::lang::operator "" _j(const char16_t* p, size_t n)
{
    return new String(new ::char16_tArray(p, static_cast<int>(n)));
}

void init_jvm()
{
}

// main's String[] from the C arguments; bytes are taken as Latin-1
::java::lang::StringArray* make_args(int argc, char** argv)
{
    auto args = java::lang::System::j2cArgs(argc > 0 ? argc - 1 : 0);
    for (int i = 1; i < argc; i++) {
        int n = static_cast<int>(std::strlen(argv[i]));
        auto chars = new ::char16_tArray(n);
        for (int j = 0; j < n; j++) {
            (*chars)[j] = static_cast<unsigned char>(argv[i][j]);
        }
        args->set(i - 1, new java::lang::String(chars));
    }
    return args;
}
