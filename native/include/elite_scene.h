#ifndef ELITE_SCENE_H
#define ELITE_SCENE_H
#include "elite_flight.h"

#define ELITE_SCENE_STARS 64
#define ELITE_SCENE_MAGIC 0x45464c54u

/* A flight TEST SCENE, not update_universe: one real model and synthetic
 * reference points, no combat, collisions, planets, market or save format.
 * State belongs to the caller, never a global/JNI pointer handle. */
typedef struct {
    uint32_t magic, version, model, profile, ticks, random;
    elite_controls_state controls;
    elite_motion_state motion;
    stellarobject_t target;
    vector32_t stars[ELITE_SCENE_STARS];
    uint32_t fault;
} elite_scene_state;

ELITE_API uint32_t elite_scene_size(void);
ELITE_API int elite_scene_reset(elite_scene_state *scene, uint32_t model, uint32_t profile);
ELITE_API int elite_scene_step(elite_scene_state *scene, const elite_controls_input *input, uint32_t ticks);
#endif
