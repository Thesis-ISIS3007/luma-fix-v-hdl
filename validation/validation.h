#ifndef VALIDATION_H
#define VALIDATION_H

void *memset(void *buf, int value, int size) {
    unsigned char *p = buf;
    unsigned char v = (unsigned char)value;

    for (int i = 0; i < size; ++i) {
        p[i] = v;
    }
    return buf;
}

void *memcpy(void *dst, const void *src, int size) {
    unsigned char *d = dst;
    const unsigned char *s = src;

    for (int i = 0; i < size; ++i) {
        d[i] = s[i];
    }
    return dst;
}

#endif