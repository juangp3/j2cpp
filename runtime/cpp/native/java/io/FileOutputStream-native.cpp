// Natives of java.io.FileOutputStream
#include <unistd.h>

#include <Array.hpp>
#include <java/io/FileOutputStream.hpp>

void java::io::FileOutputStream::writeBytes(int32_t fd, ::int8_tArray* b, int32_t off, int32_t len)
{
    clinit();
    const int8_t* p = b->begin() + off;
    while (len > 0) {
        ssize_t written = ::write(fd, p, len);
        if (written <= 0) {
            return;
        }
        p += written;
        len -= static_cast<int32_t>(written);
    }
}
