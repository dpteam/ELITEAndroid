#include "elite_core.h"
#include <stddef.h>
#include <limits.h>

_Static_assert(CHAR_BIT == 8 && sizeof(int) == 4, "Requires 8-bit bytes and 32-bit int");
_Static_assert(sizeof(vector32_t) == 12, "Vector layout must not expand on LP64");
_Static_assert(sizeof(matrix_t) == 36, "Matrix layout must not expand on LP64");
_Static_assert(offsetof(stellarobject_t, rotx) == 56, "APK roll field offset");
_Static_assert(offsetof(stellarobject_t, rotz) == 57, "APK pitch field offset");
_Static_assert(offsetof(stellarobject_t, type) == 62, "APK ship type field offset");
_Static_assert(sizeof(stellarobject_t) == 72, "APK stellarobject stride");
_Static_assert(sizeof(vertex_t) == 20 && sizeof(line_t) == 8 && sizeof(face_t) == 16,
               "Numeric geometry layout must remain stable");

int elite_core_version(void) { return 1; }

uint32_t elite_sqrt_u32(uint32_t value) {
    /* Integer square root without floating-point rounding/ABI differences. */
    uint32_t result = 0;
    uint32_t bit = 1u << 30;
    while (bit > value) bit >>= 2;
    while (bit) {
        if (value >= result + bit) {
            value -= result + bit;
            result = (result >> 1) + bit;
        } else {
            result >>= 1;
        }
        bit >>= 2;
    }
    return result;
}

uint32_t elite_churn_seed(uint8_t seed[6]) {
    /* APK x86 churn_seed and AGB churn.S both truncate the intermediate high
       sum before adding the third word. Preserve that carry convention. */
    uint32_t low = seed[0] + seed[2];
    uint32_t high = (seed[1] + seed[3] + (low >> 8)) & 255u;
    low = (low & 255u) + seed[4];
    high += seed[5] + (low >> 8);
    seed[0] = seed[2]; seed[1] = seed[3];
    seed[2] = seed[4]; seed[3] = seed[5];
    seed[4] = (uint8_t)low; seed[5] = (uint8_t)high;
    return high >> 8;
}
