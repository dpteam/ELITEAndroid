#include "elite_core.h"
#include "elite_mesh.h"
#include "elite_scene.h"
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
    CHECK(elite_mesh_frame_size() == 4636);
    CHECK(elite_model_count() == 66);
    CHECK(elite_model_at(elite_model_count()) == NULL);
    CHECK(elite_model_name(elite_model_count()) == NULL);
    elite_mesh_frame frame;
    uint32_t model = 0;
    for (uint32_t i = 0; i < elite_model_count(); ++i)
        if (strcmp(elite_model_name(i), "ship0") == 0) model = i;
    memset(&ship, 0, sizeof ship);
    ship.rotmat[0].x = ship.rotmat[1].y = ship.rotmat[2].z = 24576;
    ship.location.z = 360;
    ship.type = 1;
    stellarobject_t before = ship;
    CHECK(elite_mesh_build(model, &ship, ELITE_MESH_SOLID, &frame) == ELITE_MESH_OK);
    CHECK(frame.vertex_count > 0 && frame.command_count > 0);
    CHECK(memcmp(&before, &ship, sizeof ship) == 0);
    for (unsigned i = 0; i < frame.command_count; ++i) {
        CHECK(frame.commands[i].count <= ELITE_MESH_CAPACITY);
        for (unsigned j = 0; j < frame.commands[i].count; ++j)
            CHECK(frame.commands[i].indices[j] < frame.vertex_count);
    }
    CHECK(elite_mesh_build(UINT32_MAX, &ship, 1, &frame) == ELITE_MESH_INVALID);
    CHECK(elite_mesh_build(model, NULL, 1, &frame) == ELITE_MESH_INVALID);
    CHECK(elite_mesh_build(model, &ship, 1, NULL) == ELITE_MESH_INVALID);
    CHECK(elite_mesh_build(model, &ship, 8, &frame) == ELITE_MESH_INVALID);
    ship.location.z = -360;
    CHECK(elite_mesh_build(model, &ship, 1, &frame) == ELITE_MESH_CULLED);
    ship = before; ship.location.x = 0x7fffffff;
    CHECK(elite_mesh_build(model, &ship, 1, &frame) == ELITE_MESH_INVALID);
    ship = before; ship.missiles = 0x20;
    CHECK(elite_mesh_build(model, &ship, 1, &frame) == ELITE_MESH_EFFECT_UNSUPPORTED);
    ship = before; memset(ship.rotmat, 0, sizeof ship.rotmat);
    CHECK(elite_mesh_build(model, &ship, 1, &frame) == ELITE_MESH_INVALID);
    elite_scene_state scene;
    CHECK(elite_scene_size() == sizeof scene && elite_scene_size() == 912);
    CHECK(elite_scene_reset(&scene, model, 2) == ELITE_FLIGHT_OK);
    CHECK(scene.magic == ELITE_SCENE_MAGIC && scene.ticks == 0 && scene.fault == 0);
    elite_controls_input flight = {0, 1, 0, 0, 1, 0, 0, 0, 0};
    CHECK(elite_scene_step(&scene, &flight, 1) == ELITE_FLIGHT_OK);
    CHECK(scene.ticks == 1 && scene.fault == 0);
    CHECK(elite_scene_step(&scene, &flight, 4) == ELITE_FLIGHT_OK);
    CHECK(scene.ticks == 5);
    CHECK(elite_scene_step(&scene, &flight, 5) == ELITE_FLIGHT_INVALID);
    scene.magic = 0;
    CHECK(elite_scene_step(&scene, &flight, 1) == ELITE_FLIGHT_INVALID);
    CHECK(elite_controls_reset(NULL, 8) == ELITE_FLIGHT_INVALID);
    elite_controls_state controls = {0};
    elite_controls_input invalid = {0};
    CHECK(elite_controls_reset(&controls, -1) == ELITE_FLIGHT_INVALID);
    CHECK(elite_controls_reset(&controls, 8) == ELITE_FLIGHT_OK);
    elite_controls_state copy = controls;
    invalid.tilt_roll = INT32_MIN;
    CHECK(elite_controls_step(&controls, &invalid, 0) == ELITE_FLIGHT_INVALID);
    CHECK(memcmp(&copy, &controls, sizeof copy) == 0);
    invalid.tilt_roll = 0; invalid.accel = 1;
    CHECK(elite_controls_step(&controls, &invalid, 0) == ELITE_FLIGHT_OK);
    CHECK(controls.speed == 8 && controls.pending_accel == 1);
    invalid.accel = 0;
    CHECK(elite_controls_step(&controls, &invalid, 0) == ELITE_FLIGHT_OK);
    CHECK(controls.speed == 9 && controls.pending_accel == 0);
    CHECK(elite_profile_at(15) == NULL);
    ship = before;
    elite_motion_state motion = {0, 0, 8, 255, 0};
    CHECK(elite_object_step(model, &ship, &motion, 2) == ELITE_FLIGHT_OK);
    CHECK(ship.location.z == before.location.z - 8);
    ship.rotmat[1] = ship.rotmat[2];
    stellarobject_t bad_ship = ship;
    CHECK(elite_object_step(model, &ship, &motion, 2) == ELITE_FLIGHT_INVALID);
    CHECK(memcmp(&ship, &bad_ship, sizeof ship) == 0);
    CHECK(elite_scene_reset(&scene, model, 0) == ELITE_FLIGHT_OK);
    scene.stars[0].x = INT32_MIN;
    CHECK(elite_scene_step(&scene, &flight, 1) == ELITE_FLIGHT_INVALID);
    printf("geometry_checksum=%08x\n", elite_geometry_checksum());
    printf("OK: layouts, 100000 square roots, seed, rotation, autopilot, mesh and invalid-input guards\n");
    return 0;
}
