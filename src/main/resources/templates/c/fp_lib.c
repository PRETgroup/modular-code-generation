#include "fp_lib.h"

#include <stdlib.h>
#include <stdio.h>
#include <math.h>

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

    if(z == 0) {
        printf("Division by zero\n");
        return 0;
    }

    uint64_t quotient = 0;
    uint64_t remainder = 0;

    for(int i=3*FP_BITS-1; i>=0; i--) {
        remainder <<= 1;

        if(i >= FP_BITS) {
            remainder += (a >> (i - FP_BITS)) & 0b1;
        }

        if(remainder >= b) {
            remainder -= b;
            quotient |= (1UL << i);
        }
    }

    if((y < 0) != (z < 0)) {
        return quotient * -1;
    }
    else {
        return quotient;
    }
}

int64_t FP_FLOOR(int64_t x) {
    int64_t a = x >> FP_BITS;

    return (a << FP_BITS);
}

int64_t FP_CEIL(int64_t x) {
    if((x & ((1L << FP_BITS) - 1)) == 0) {
        return x;
    }

    int64_t a = (x >> FP_BITS) + 1;

    return (a << FP_BITS);
}
