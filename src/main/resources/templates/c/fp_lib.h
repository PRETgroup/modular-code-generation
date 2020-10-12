#ifndef FP_LIB_H_
#define FP_LIB_H_

#include <stdint.h>

#define FP_BITS {{FP_BITS}}

#define CREATE_FP(x) ((int64_t) (x * (1L << FP_BITS)))
#define FROM_FP(x) (x / pow(2, FP_BITS))

extern uint64_t hi(uint64_t x);
extern uint64_t lo(uint64_t x);
extern int64_t FP_MULT(int64_t y, int64_t z);
extern int64_t FP_DIV(int64_t y, int64_t z);
extern int64_t FP_FLOOR(int64_t x);

#endif // FP_LIB_H_