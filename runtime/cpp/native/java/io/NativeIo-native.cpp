// Natives of java.io.NativeIo: POSIX calls for the runtime's files and streams.
// They report errno and leave the exceptions to the Java side.
#include <cerrno>
#include <cstring>
#include <string>

#include <dirent.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <Array.hpp>
#include <java/io/NativeIo.hpp>
#include <java/lang/String.hpp>

#include "j2c_runtime.hpp"

namespace {

const char* cpath(::int8_tArray* path)
{
    return reinterpret_cast<const char*>(path->begin());
}

}

int32_t java::io::NativeIo::errnoValue(int32_t index)
{
    static const int values[] = { ENOENT, EACCES, EEXIST, ENOTDIR, EISDIR, ENOTEMPTY };
    return values[index];
}

int32_t java::io::NativeIo::open(::int8_tArray* path, int32_t flags)
{
    clinit();
    int mode = 0;
    if ((flags & WRITE) != 0) {
        mode = (flags & READ) != 0 ? O_RDWR : O_WRONLY;
    } else {
        mode = O_RDONLY;
    }
    if ((flags & APPEND) != 0) {
        mode |= O_APPEND;
    }
    if ((flags & CREATE_NEW) != 0) {
        mode |= O_CREAT | O_EXCL;
    } else if ((flags & CREATE) != 0) {
        mode |= O_CREAT;
    }
    if ((flags & TRUNCATE) != 0) {
        mode |= O_TRUNC;
    }
    int fd;
    do {
        fd = ::open(cpath(path), mode | O_CLOEXEC, 0666);
    } while (fd < 0 && errno == EINTR);
    if (fd < 0) {
        return -errno;
    }
    if ((flags & REJECT_DIRECTORY) != 0) {
        struct stat st;
        if (::fstat(fd, &st) == 0 && S_ISDIR(st.st_mode)) {
            ::close(fd);
            return -EISDIR;
        }
    }
    return fd;
}

int32_t java::io::NativeIo::read(int32_t fd, ::int8_tArray* b, int32_t off, int32_t len)
{
    clinit();
    ssize_t n;
    do {
        n = ::read(fd, b->begin() + off, static_cast<size_t>(len));
    } while (n < 0 && errno == EINTR);
    return n < 0 ? -errno : static_cast<int32_t>(n);
}

int32_t java::io::NativeIo::write(int32_t fd, ::int8_tArray* b, int32_t off, int32_t len)
{
    clinit();
    const int8_t* p = b->begin() + off;
    while (len > 0) {
        ssize_t written = ::write(fd, p, static_cast<size_t>(len));
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            return errno;
        }
        p += written;
        len -= static_cast<int32_t>(written);
    }
    return 0;
}

int32_t java::io::NativeIo::close(int32_t fd)
{
    clinit();
    return ::close(fd) == 0 ? 0 : errno;
}

int32_t java::io::NativeIo::available(int32_t fd)
{
    clinit();
    struct stat st;
    if (::fstat(fd, &st) != 0) {
        return -errno;
    }
    if (S_ISREG(st.st_mode)) {
        off_t position = ::lseek(fd, 0, SEEK_CUR);
        if (position < 0) {
            return -errno;
        }
        off_t left = st.st_size - position;
        return left <= 0 ? 0 : left > 0x7fffffff ? 0x7fffffff : static_cast<int32_t>(left);
    }
    int n = 0;
    if (::ioctl(fd, FIONREAD, &n) != 0) {
        return 0;
    }
    return n;
}

int64_t java::io::NativeIo::skip(int32_t fd, int64_t n)
{
    clinit();
    off_t current = ::lseek(fd, 0, SEEK_CUR);
    if (current < 0) {
        return -errno;
    }
    off_t moved = ::lseek(fd, static_cast<off_t>(n), SEEK_CUR);
    if (moved < 0) {
        return -errno;
    }
    return static_cast<int64_t>(moved - current);
}

int32_t java::io::NativeIo::kind(::int8_tArray* path)
{
    clinit();
    struct stat st;
    if (::stat(cpath(path), &st) != 0) {
        return errno == ENOENT || errno == ENOTDIR ? MISSING : -errno;
    }
    if (S_ISREG(st.st_mode)) {
        return REGULAR;
    }
    return S_ISDIR(st.st_mode) ? DIRECTORY : OTHER;
}

int64_t java::io::NativeIo::size(::int8_tArray* path)
{
    clinit();
    struct stat st;
    if (::stat(cpath(path), &st) != 0) {
        return -errno;
    }
    return static_cast<int64_t>(st.st_size);
}

int64_t java::io::NativeIo::lastModified(::int8_tArray* path)
{
    clinit();
    struct stat st;
    if (::stat(cpath(path), &st) != 0) {
        return -errno;
    }
    return static_cast<int64_t>(st.st_mtim.tv_sec) * 1000 + st.st_mtim.tv_nsec / 1000000;
}

int32_t java::io::NativeIo::remove(::int8_tArray* path)
{
    clinit();
    struct stat st;
    if (::lstat(cpath(path), &st) != 0) {
        return errno;
    }
    int result = S_ISDIR(st.st_mode) ? ::rmdir(cpath(path)) : ::unlink(cpath(path));
    // Linux reports EEXIST or ENOTEMPTY for a directory with entries
    return result == 0 ? 0 : errno == EEXIST ? ENOTEMPTY : errno;
}

int32_t java::io::NativeIo::mkdir(::int8_tArray* path)
{
    clinit();
    return ::mkdir(cpath(path), 0777) == 0 ? 0 : errno;
}

int32_t java::io::NativeIo::rename(::int8_tArray* from, ::int8_tArray* to)
{
    clinit();
    return ::rename(cpath(from), cpath(to)) == 0 ? 0 : errno;
}

int32_t java::io::NativeIo::access(::int8_tArray* path, int32_t mode)
{
    clinit();
    return ::access(cpath(path), mode) == 0 ? 0 : errno;
}

::int8_tArray* java::io::NativeIo::list(::int8_tArray* path)
{
    clinit();
    DIR* dir = ::opendir(cpath(path));
    if (dir == nullptr) {
        return nullptr;
    }
    std::string names;
    while (struct dirent* entry = ::readdir(dir)) {
        if (std::strcmp(entry->d_name, ".") == 0 || std::strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        names += entry->d_name;
        names += '\0';
    }
    ::closedir(dir);
    return new ::int8_tArray(reinterpret_cast<const int8_t*>(names.data()), static_cast<int>(names.size()));
}

::int8_tArray* java::io::NativeIo::cwd()
{
    clinit();
    std::string buffer(256, '\0');
    while (::getcwd(&buffer[0], buffer.size()) == nullptr) {
        if (errno != ERANGE) {
            return new ::int8_tArray(0);
        }
        buffer.resize(buffer.size() * 2);
    }
    size_t n = std::strlen(buffer.c_str());
    return new ::int8_tArray(reinterpret_cast<const int8_t*>(buffer.data()), static_cast<int>(n));
}

java::lang::String* java::io::NativeIo::errorText(int32_t error)
{
    clinit();
    const char* text = std::strerror(error);
    std::u16string result;
    for (const char* p = text; *p != '\0'; p++) {
        result += static_cast<char16_t>(static_cast<unsigned char>(*p));
    }
    return j2c_string(result);
}
