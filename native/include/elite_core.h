#ifndef ELITE_CORE_H
#define ELITE_CORE_H

#include <stdint.h>
#include "motion.h"

#if defined(_WIN32)
#define ELITE_API __declspec(dllexport)
#else
#define ELITE_API __attribute__((visibility("default")))
#endif

/* No JNI registration: this library is a tested subset, not the full game. */
ELITE_API int elite_core_version(void);
ELITE_API uint32_t elite_sqrt_u32(uint32_t value);
/* Six bytes in original seed_t order; returns the final carry. */
ELITE_API uint32_t elite_churn_seed(uint8_t seed[6]);
/* Hash all numeric ship geometry independent of host pointer width/endian. */
ELITE_API uint32_t elite_geometry_checksum(void);

void fly_to_vector_final(stellarobject_t *ship, vector32_t *target, int angle, int limit);
void fly_to_vector(stellarobject_t *ship, vector32_t *target);
void flip_vect(vector32_t *vector);
void norm_big(vector32_t *vector, int x, int y, int z);
void dot_product(vector32_t *out, vector32_t *in, matrix_t matrix);

#endif
