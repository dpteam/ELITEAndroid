#include "elite_session.h"
#include <stddef.h>
#include <string.h>

_Static_assert(offsetof(elite_session_state, station) == 8, "Session station ABI");
_Static_assert(offsetof(elite_session_state, scene) == 212, "Session scene ABI");
_Static_assert(sizeof(elite_session_state) == 1124, "Session ABI drift");

uint32_t elite_session_size(void) { return sizeof(elite_session_state); }

int elite_session_valid(const elite_session_state *state) {
    if (!state || state->magic != ELITE_SESSION_MAGIC || state->version != 1 ||
        state->station.magic != ELITE_STATION_MAGIC || state->station.version != 1 ||
        state->station.docked > 1 || state->station.system_index > 255 ||
        state->station.info.economy > 7 || state->station.system_name[11] != 0 ||
        state->scene.profile != state->station.commander.ship ||
        !elite_profile_at(state->station.commander.ship)) return 0;
    for (unsigned i = 0; i < ELITE_GOODS_COUNT; ++i)
        if (state->station.prices[i] > 1020 || state->station.prices[i] % 4) return 0;
    elite_scene_state scene = state->scene;
    elite_controls_input neutral = {0};
    if (elite_scene_step(&scene, &neutral, 0) ||
        elite_controls_step(&scene.controls, &neutral, scene.profile) ||
        elite_object_step(scene.model, &scene.target, &scene.motion, 2)) return 0;
    return 1;
}

int elite_session_reset(elite_session_state *state, uint32_t model) {
    if (!state || !elite_model_at(model)) return ELITE_SESSION_INVALID;
    elite_session_state next = {0};
    next.magic = ELITE_SESSION_MAGIC; next.version = 1;
    if (elite_station_reset(&next.station) ||
        elite_scene_reset(&next.scene, model, next.station.commander.ship)) return ELITE_SESSION_INVALID;
    *state = next;
    return ELITE_SESSION_OK;
}

int elite_session_launch(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (!state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    elite_scene_state scene;
    if (elite_scene_reset(&scene, state->scene.model, state->station.commander.ship))
        return ELITE_SESSION_INVALID;
    state->scene = scene;
    state->station.docked = 0;
    return ELITE_SESSION_OK;
}

int elite_session_return_test(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    state->station.docked = 1;
    return ELITE_SESSION_OK;
}

int elite_session_reset_flight(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    return elite_scene_reset(&state->scene, state->scene.model, state->station.commander.ship)
        ? ELITE_SESSION_INVALID : ELITE_SESSION_OK;
}

int elite_session_step(elite_session_state *state, const elite_controls_input *input, uint32_t ticks) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    // Legacy scene_step can set a fault after partially updating a tick. Do not
    // publish that partial update as the session's authoritative state.
    elite_scene_state next = state->scene;
    if (elite_scene_step(&next, input, ticks)) return ELITE_SESSION_INVALID;
    elite_session_state candidate = *state;
    candidate.scene = next;
    if (!elite_session_valid(&candidate)) return ELITE_SESSION_INVALID;
    state->scene = next;
    return ELITE_SESSION_OK;
}

int elite_session_trade(elite_session_state *state, uint32_t item, int buy) {
    if (!elite_session_valid(state)) return ELITE_TRADE_INVALID;
    return elite_station_trade(&state->station, item, buy);
}
