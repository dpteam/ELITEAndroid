#ifndef ELITE_FLIGHT_H
#define ELITE_FLIGHT_H
#include "elite_mesh.h"
#include "upgrade.h"

/* Explicit state replaces the original process globals. All members are int32
 * so host, ARM32 and LP64 execution share the same snapshot representation. */
typedef struct {
    int32_t alpha, beta, delta, counter, total_beta;
} elite_motion_state;

typedef struct {
    int32_t roll, pitch, pending_accel, alpha, beta, speed;
} elite_controls_state;

typedef struct {
    int32_t right, left, up, down, accel;
    int32_t tilt_roll, tilt_pitch, autopilot, hyperspace;
} elite_controls_input;

enum { ELITE_FLIGHT_OK = 0, ELITE_FLIGHT_INVALID = 1 };

ELITE_API uint32_t elite_profile_count(void);
ELITE_API const player_ship_t *elite_profile_at(uint32_t profile);
ELITE_API int elite_controls_reset(elite_controls_state *state, int speed);
/* Control math only: no laser/menu/autopilot-AI callbacks are invoked. */
ELITE_API int elite_controls_step(elite_controls_state *state,
                                 const elite_controls_input *input, uint32_t profile);
/* One original mveit tick, using the model's speed limit. Does not advance
 * the shared frame counter: all objects in one tick must see the same value.
 * Invalid input is rejected without modifying the object or motion state. */
ELITE_API int elite_object_step(uint32_t model, stellarobject_t *object,
                               elite_motion_state *motion, int index);

/* Imported implementations; callers must use the validated public entry. */
void elite_mveit_raw(stellarobject_t *object, const shipheader_t *data,
                    int index, elite_motion_state *motion);
void dampen(int *value);
void bump(int *value, int increment);
void redu(int *value, int increment);
#endif
