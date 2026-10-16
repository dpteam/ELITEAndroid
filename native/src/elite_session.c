#include "elite_session.h"
#include <stddef.h>
#include <string.h>

_Static_assert(offsetof(elite_session_state, station) == 8, "Session station ABI");
_Static_assert(offsetof(elite_session_state, scene) == 212, "Session scene ABI");
_Static_assert(offsetof(elite_session_state, navigation) == 1124, "Session navigation ABI");
_Static_assert(offsetof(elite_session_state, docking) == 1160, "Session docking ABI");
_Static_assert(sizeof(elite_session_state) == 1184, "Session ABI drift");

uint32_t elite_session_size(void) { return sizeof(elite_session_state); }

int elite_session_valid(const elite_session_state *state) {
    if (!state || state->magic != ELITE_SESSION_MAGIC || state->version != 1 ||
        state->station.magic != ELITE_STATION_MAGIC || state->station.version != 1 ||
        state->station.docked > 1 || state->station.system_index > 255 ||
        state->station.info.economy > 7 || state->station.system_name[11] != 0 ||
        state->scene.profile != state->station.commander.ship ||
        !elite_profile_at(state->station.commander.ship) ||
        state->navigation.target_index > 255 || state->docking.distance > ELITE_DOCK_DISTANCE ||
        state->docking.speed > 36 || state->docking.alignment > 255 ||
        state->docking.status > ELITE_DOCK_DOCKED || state->docking.reserved != 0 ||
        (state->station.docked && (state->docking.distance != 0 || state->docking.speed != 0 ||
                                   state->docking.alignment != 0 || state->docking.ticks != 0)) ||
        (state->station.docked && state->docking.status != ELITE_DOCK_DOCKED) ||
        (!state->station.docked && state->docking.status == ELITE_DOCK_DOCKED)) return 0;
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
    if (elite_navigation_select(&next.station.commander, next.station.system_index,
                                &next.navigation) == ELITE_NAV_INVALID) return ELITE_SESSION_INVALID;
    next.docking.status = ELITE_DOCK_DOCKED;
    *state = next;
    return ELITE_SESSION_OK;
}

int elite_session_select_target(elite_session_state *state, uint32_t index) {
    if (!elite_session_valid(state) || state->station.docked != 1)
        return ELITE_SESSION_INVALID;
    return elite_navigation_select(&state->station.commander, index, &state->navigation)
        == ELITE_NAV_INVALID ? ELITE_SESSION_INVALID : ELITE_SESSION_OK;
}

int elite_session_select_reachable(elite_session_state *state) {
    if (!elite_session_valid(state) || state->station.docked != 1)
        return ELITE_SESSION_INVALID;
    return elite_navigation_find_reachable(&state->station.commander, &state->navigation)
        == ELITE_NAV_OK ? ELITE_SESSION_OK : ELITE_SESSION_INVALID;
}

int elite_session_jump(elite_session_state *state) {
    if (!elite_session_valid(state) || state->station.docked != 0)
        return ELITE_SESSION_WRONG_PHASE;
    if (elite_navigation_jump(&state->station, &state->navigation) != ELITE_NAV_OK)
        return ELITE_SESSION_INVALID;
    elite_scene_state scene;
    if (elite_scene_reset(&scene, state->scene.model, state->station.commander.ship))
        return ELITE_SESSION_INVALID;
    state->scene = scene;
    state->docking.distance = ELITE_DOCK_DISTANCE;
    state->docking.speed = scene.controls.speed;
    state->docking.alignment = 0;
    state->docking.ticks = 0;
    state->docking.status = ELITE_DOCK_APPROACH;
    state->docking.reserved = 0;
    return ELITE_SESSION_OK;
}

int elite_session_launch(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (!state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    elite_scene_state scene;
    if (elite_scene_reset(&scene, state->scene.model, state->station.commander.ship))
        return ELITE_SESSION_INVALID;
    state->scene = scene;
    state->docking.distance = ELITE_DOCK_DISTANCE;
    state->docking.speed = scene.controls.speed;
    state->docking.alignment = 0;
    state->docking.ticks = 0;
    state->docking.status = ELITE_DOCK_APPROACH;
    state->docking.reserved = 0;
    state->station.docked = 0;
    return ELITE_SESSION_OK;
}

int elite_session_return_test(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    state->station.docked = 1;
    state->docking.distance = 0;
    state->docking.speed = 0;
    state->docking.alignment = 0;
    state->docking.ticks = 0;
    state->docking.status = ELITE_DOCK_DOCKED;
    return ELITE_SESSION_OK;
}

int elite_session_approach(elite_session_state *state, uint32_t ticks) {
    if (!elite_session_valid(state) || ticks > 120) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    if (state->docking.status == ELITE_DOCK_READY) return ELITE_SESSION_OK;
    elite_session_state next = *state;
    for (uint32_t i = 0; i < ticks; ++i) {
        uint32_t speed = next.docking.speed;
        uint32_t travel = speed * 4;
        next.docking.distance = travel >= next.docking.distance ? 0 : next.docking.distance - travel;
        if (speed > 2) next.docking.speed -= 1;
        if (next.docking.alignment > 0) next.docking.alignment -= next.docking.alignment > 4 ? 4 : next.docking.alignment;
        ++next.docking.ticks;
    }
    if (next.docking.distance == 0 && next.docking.speed <= 2 && next.docking.alignment <= 4)
        next.docking.status = ELITE_DOCK_READY;
    if (!elite_session_valid(&next)) return ELITE_SESSION_INVALID;
    *state = next;
    return ELITE_SESSION_OK;
}

int elite_session_dock(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    if (state->docking.status != ELITE_DOCK_READY) return ELITE_SESSION_NOT_READY;
    state->station.docked = 1;
    state->docking.distance = 0;
    state->docking.speed = 0;
    state->docking.alignment = 0;
    state->docking.ticks = 0;
    state->docking.status = ELITE_DOCK_DOCKED;
    return ELITE_SESSION_OK;
}

int elite_session_reset_flight(elite_session_state *state) {
    if (!elite_session_valid(state)) return ELITE_SESSION_INVALID;
    if (state->station.docked) return ELITE_SESSION_WRONG_PHASE;
    if (elite_scene_reset(&state->scene, state->scene.model, state->station.commander.ship))
        return ELITE_SESSION_INVALID;
    state->docking.distance = ELITE_DOCK_DISTANCE;
    state->docking.speed = state->scene.controls.speed;
    state->docking.alignment = 0; state->docking.ticks = 0;
    state->docking.status = ELITE_DOCK_APPROACH; state->docking.reserved = 0;
    return ELITE_SESSION_OK;
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
