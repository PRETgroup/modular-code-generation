#include "fp_lib.h"

uint64_t hi(uint64_t x) {
    return x >> FP_BITS;
}

uint64_t lo(uint64_t x) {
    return ((1L << FP_BITS) - 1) & x;
}

int64_t FP_MULT(int64_t y, int64_t z) {
    uint64_t a = labs(y);
    uint64_t b = labs(z);

    uint64_t s0, s1, s2, s3;

    s0 = (lo(a) * lo(b)) >> FP_BITS;
    s1 = hi(a) * lo(b);
    s2 = lo(a) * hi(b);
    s3 = (hi(a) * hi(b)) << FP_BITS;

    int64_t result = s0 + s1 + s2 + s3;

    if((y < 0) != (z < 0)) {
        return result * -1;
    }
    else {
        return result;
    }
}

int64_t FP_DIV(int64_t y, int64_t z) {
    uint64_t a = labs(y);
    uint64_t b = labs(z);

    lldiv_t res;
    res = lldiv (a, b);

    uint64_t s0 = res.quot;
    uint64_t upper = hi(res.rem);

    res = lldiv(lo(res.rem) << FP_BITS, b);

    int64_t result = (s0 << FP_BITS) + res.quot;

    if(b >> FP_BITS > 0) {
        res = lldiv(upper << FP_BITS, b >> FP_BITS);
        result += res.quot;
    }


    if((y < 0) != (z < 0)) {
        return result * -1;
    }
    else {
        return result;
    }
}

int64_t FP_FLOOR(int64_t x) {
    int64_t a = x / (1L << FP_BITS);

    return (a << FP_BITS);
}