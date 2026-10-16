#include "elite_core.h"
#include <stdio.h>
#include <string.h>
#include <stddef.h>

#define CHECK(expr) do { if (!(expr)) { fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #expr); return 1; } } while (0)

int main(void) {
    CHECK(elite_core_version() == 1);
    CHECK(sizeof(vector32_t) == 12);
    CHECK(sizeof(stellarobject_t) == 72);
    CHECK(elite_sqrt_u32(0) == 0);
    CHECK(elite_sqrt_u32(1) == 1);
    CHECK(elite_sqrt_u32(65535) == 255);
    CHECK(elite_sqrt_u32(UINT32_MAX) == 65535);
    for (uint32_t i = 0; i < 100000; ++i) {
        uint32_t root = elite_sqrt_u32(i);
        CHECK((uint64_t)root * root <= i);
        CHECK((uint64_t)(root + 1) * (root + 1) > i);
    }
    uint8_t seed[6] = {0x4a, 0x5a, 0x48, 0x02, 0x53, 0xb7};
    const uint8_t next[6] = {0x48, 0x02, 0x53, 0xb7, 0xe5, 0x13};
    CHECK(elite_churn_seed(seed) == 1);
    CHECK(memcmp(seed, next, sizeof seed) == 0);

    vector32_t zero = {0, 0, 0};
    CHECK(norm(&zero) == 1);
    norm_big(&zero, 0, 0, 0);
    CHECK(zero.x == 0 && zero.y == 0 && zero.z == 0);
    vector32_t axis = {0, 0, 0x6000};
    CHECK(norm(&axis) == 96 && axis.z == 0x6000);
    int32_t x = 0x6000, y = 0;
    fixed_rotate(&x, &y, 0);
    CHECK(x == 24528 && y == -1536);

    stellarobject_t ship = {0};
    ship.rotmat[0].x = ship.rotmat[1].y = ship.rotmat[2].z = 0x6000;
    ship.type = 1;
    vector32_t target = {0, 0, 96};
    fly_to_vector(&ship, &target);
    CHECK(ship.accel == 3);
    fly_to_vector_final(&ship, &target, -0x2000, 6);
    CHECK(ship.rotz == 0x87 && ship.rotx == 0);

    CHECK(elite_geometry_checksum() == 0xac59f864u);
    printf("geometry_checksum=%08x\n", elite_geometry_checksum());
    printf("OK: fixed-width layouts, 100000 square roots, seed, rotation, autopilot, zero guards\n");
    return 0;
}
