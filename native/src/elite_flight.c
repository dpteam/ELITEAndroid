/* Android control math reconciled with APK update_motion/dampen_controls,
 * not a verbatim copy of GBA input.c (whose acceleration order differs).
 * See native/ORIGIN.md and differential replay reports.
 */
#include "elite_flight.h"
#include <stddef.h>
#include <string.h>

_Static_assert(sizeof(elite_motion_state) == 20, "Motion ABI drift");
_Static_assert(sizeof(elite_controls_state) == 24, "Control ABI drift");
_Static_assert(sizeof(elite_controls_input) == 36, "Input ABI drift");
_Static_assert(sizeof(player_ship_t) == 18, "Player profile ABI drift");
_Static_assert(offsetof(player_ship_t, max_speed) == 12, "APK speed profile offset");
_Static_assert(offsetof(player_ship_t, turning_circle) == 16, "APK turning profile offset");

static int abs_small(int x) { return x < 0 ? -x : x; }
static int clamp(int x, int low, int high) { return x < low ? low : x > high ? high : x; }

uint32_t elite_profile_count(void) { return 15; }
const player_ship_t *elite_profile_at(uint32_t index) {
    return index < elite_profile_count() ? &player_ship_data[index] : NULL;
}

int elite_controls_reset(elite_controls_state *state, int speed) {
    if (!state || speed < 0 || speed > 255) return ELITE_FLIGHT_INVALID;
    memset(state, 0, sizeof(*state));
    state->speed = speed;
    return ELITE_FLIGHT_OK;
}

int elite_controls_step(elite_controls_state *state, const elite_controls_input *input, uint32_t index) {
    const player_ship_t *profile = elite_profile_at(index);
    if (!state || !input || !profile || state->roll < -128 || state->roll > 128 ||
        state->pitch < -128 || state->pitch > 128 || state->pending_accel < -127 ||
        state->pending_accel > 127 || state->speed < 0 || state->speed > 255 ||
        input->right < 0 || input->right > 127 || input->left < 0 || input->left > 127 ||
        input->up < 0 || input->up > 127 || input->down < 0 || input->down > 127 ||
        input->accel < -127 || input->accel > 127 || input->tilt_roll < -128 || input->tilt_roll > 128 ||
        input->tilt_pitch < -128 || input->tilt_pitch > 128 ||
        input->autopilot < 0 || input->autopilot > 1 || input->hyperspace < 0 || input->hyperspace > 1)
        return ELITE_FLIGHT_INVALID;
    int max_turn = 127 - (uint8_t)(profile->turning_circle ^ 255);
    if (!input->autopilot && !input->hyperspace && (input->tilt_roll || input->tilt_pitch)) {
        state->roll = clamp(input->tilt_roll, -max_turn / 4, max_turn / 4);
        state->pitch = clamp(input->tilt_pitch, -(max_turn + 1) / 16, (max_turn + 1) / 16);
        state->alpha = state->roll;
        state->beta = state->pitch;
    } else {
        unsigned damp = 3;
        if (input->left) {
            for (int i = 0; i < (input->left > 3 ? 3 : input->left); ++i) bump(&state->roll, 7);
            damp &= ~1u;
        }
        if (input->right) {
            for (int i = 0; i < (input->right > 3 ? 3 : input->right); ++i) redu(&state->roll, 7);
            damp &= ~1u;
        }
        if (input->up) { redu(&state->pitch, 14); damp &= ~2u; }
        if (input->down) { bump(&state->pitch, 14); damp &= ~2u; }
        if (damp & 1) { dampen(&state->roll); dampen(&state->roll); }
        else if (abs_small(state->roll) > max_turn) state->roll = state->roll < 0 ? -max_turn : max_turn;
        if (damp & 2) { dampen(&state->pitch); dampen(&state->pitch); }
        else if (abs_small(state->pitch) > max_turn + 1) state->pitch = state->pitch < 0 ? -max_turn - 1 : max_turn + 1;
        state->alpha = state->roll / 4;
        if (abs_small(state->alpha) < 8) state->alpha /= 2;
        state->beta = state->pitch / 16;
        if (abs_small(state->beta) < 3) state->beta /= 2;
    }
    // APK order: apply previous pending acceleration before queueing this tick's.
    state->speed = clamp(state->speed + state->pending_accel, 1, profile->max_speed);
    state->pending_accel = input->accel ? input->accel : 0;
    if (input->right == 127) { state->alpha = -15; state->roll = -68; }
    return ELITE_FLIGHT_OK;
}

int elite_object_step(uint32_t model, stellarobject_t *object, elite_motion_state *motion, int index) {
    const shipheader_t *data = elite_model_at(model);
    if (!data || !object || !motion || index < 0 || index > 11 || motion->alpha < -32 || motion->alpha > 32 ||
        motion->beta < -8 || motion->beta > 8 || motion->delta < -255 || motion->delta > 255 ||
        motion->counter < 0 || motion->counter > 255 || object->speed < 0 || object->speed > 127 ||
        object->accel < -127 || object->accel > 127) return ELITE_FLIGHT_INVALID;
    const vector32_t *location = &object->location;
    if (location->x < -0x100000 || location->x > 0x100000 ||
        location->y < -0x100000 || location->y > 0x100000 ||
        location->z < -0x100000 || location->z > 0x100000) return ELITE_FLIGHT_INVALID;
    for (unsigned r = 0; r < 3; ++r) {
        const vector32_t *v = &object->rotmat[r];
        if (v->x < -32768 || v->x > 32768 || v->y < -32768 || v->y > 32768 ||
            v->z < -32768 || v->z > 32768 || (v->x == 0 && v->y == 0 && v->z == 0)) return ELITE_FLIGHT_INVALID;
    }
    const vector32_t *forward = &object->rotmat[2];
    if (abs_small(forward->x) < 8192 && abs_small(forward->y) < 8192 && abs_small(forward->z) < 8192)
        return ELITE_FLIGHT_INVALID;
    // tidy() divides by the forward axis then normalizes the up axis. Parallel
    // or otherwise degenerate frames are not valid game poses.
    const vector32_t *up = &object->rotmat[1];
    int64_t cross_x = (int64_t)forward->y * up->z - (int64_t)forward->z * up->y;
    int64_t cross_y = (int64_t)forward->z * up->x - (int64_t)forward->x * up->z;
    int64_t cross_z = (int64_t)forward->x * up->y - (int64_t)forward->y * up->x;
    if (cross_x == 0 && cross_y == 0 && cross_z == 0) return ELITE_FLIGHT_INVALID;
    elite_mveit_raw(object, data, index, motion);
    return ELITE_FLIGHT_OK;
}
