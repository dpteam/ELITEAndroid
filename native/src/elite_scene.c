#include "elite_scene.h"
#include <stddef.h>
#include <string.h>

_Static_assert(sizeof(elite_scene_state) == 912, "Scene ABI drift");
_Static_assert(offsetof(elite_scene_state, target) == 68, "Scene target offset");
_Static_assert(offsetof(elite_scene_state, stars) == 140, "Scene star offset");

uint32_t elite_scene_size(void) { return sizeof(elite_scene_state); }

static uint32_t next_random(elite_scene_state *scene) {
    scene->random = scene->random * 1664525u + 1013904223u;
    return scene->random;
}
static void spawn_star(elite_scene_state *scene, vector32_t *star) {
    star->z = 1024 + (int)(next_random(scene) % 14000);
    star->x = (int)(next_random(scene) % (uint32_t)star->z) - star->z / 2;
    star->y = (int)(next_random(scene) % (uint32_t)(star->z * 2 / 3)) - star->z / 3;
}

int elite_scene_reset(elite_scene_state *scene, uint32_t model, uint32_t profile) {
    if (!scene || !elite_model_at(model) || !elite_profile_at(profile)) return ELITE_FLIGHT_INVALID;
    memset(scene, 0, sizeof(*scene));
    scene->magic = ELITE_SCENE_MAGIC; scene->version = 1;
    scene->model = model; scene->profile = profile;
    scene->random = 0xe117f;
    elite_controls_reset(&scene->controls, 8);
    scene->target.rotmat[0].x = scene->target.rotmat[1].y = scene->target.rotmat[2].z = 0x6000;
    int distance = elite_model_at(model)->size * 192;
    if (distance < 512) distance = 512;
    if (distance > 4800) distance = 4800;
    scene->target.location.z = distance;
    scene->target.type = 1;
    // Deliberately stationary target, not a fake NPC AI. Player motion alone
    // changes its relative position and orientation through original mveit().
    for (unsigned i = 0; i < ELITE_SCENE_STARS; ++i) spawn_star(scene, &scene->stars[i]);
    return ELITE_FLIGHT_OK;
}

int elite_scene_step(elite_scene_state *scene, const elite_controls_input *input, uint32_t ticks) {
    if (!scene || !input || scene->magic != ELITE_SCENE_MAGIC || scene->version != 1 ||
        !elite_model_at(scene->model) || !elite_profile_at(scene->profile) || ticks > 4 || scene->fault)
        return ELITE_FLIGHT_INVALID;
    // JNI snapshots are caller bytes, not trusted pointers/state. Reject
    // corrupted reference coordinates before entering unchecked legacy math.
    for (unsigned i = 0; i < ELITE_SCENE_STARS; ++i) {
        const vector32_t *v = &scene->stars[i];
        if (v->x < -20000 || v->x > 20000 || v->y < -20000 || v->y > 20000 ||
            v->z < 0 || v->z > 20000) return ELITE_FLIGHT_INVALID;
    }
    for (uint32_t t = 0; t < ticks; ++t) {
        if (elite_controls_step(&scene->controls, input, scene->profile)) {
            scene->fault = 1; return ELITE_FLIGHT_INVALID;
        }
        scene->motion.alpha = scene->controls.alpha;
        scene->motion.beta = scene->controls.beta;
        scene->motion.delta = scene->controls.speed;
        scene->motion.counter = (int32_t)((255u - scene->ticks) & 255u);
        if (elite_object_step(scene->model, &scene->target, &scene->motion, 2)) {
            scene->fault = 2; return ELITE_FLIGHT_INVALID;
        }
        // Synthetic reference points use the same camera-motion formulas (SUN
        // branch of mveit). This is not a port of the original star generator.
        stellarobject_t point = {0};
        point.rotmat[0].x = point.rotmat[1].y = point.rotmat[2].z = 24576;
        point.type = SUN;
        for (unsigned i = 0; i < ELITE_SCENE_STARS; ++i) {
            point.location = scene->stars[i];
            elite_mveit_raw(&point, elite_model_at(scene->model), 1, &scene->motion);
            scene->stars[i] = point.location;
            if (point.location.z < 32 || point.location.z > 20000 ||
                point.location.x < -20000 || point.location.x > 20000 ||
                point.location.y < -20000 || point.location.y > 20000)
                spawn_star(scene, &scene->stars[i]);
        }
        ++scene->ticks;
    }
    return ELITE_FLIGHT_OK;
}
