#include "elite_session.h"
#include <stdio.h>
#include <string.h>

#define CHECK(expr) do { if (!(expr)) { fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #expr); return 1; } } while (0)

int main(void) {
    uint32_t model = 0;
    for (uint32_t i = 0; i < elite_model_count(); ++i)
        if (!strcmp(elite_model_name(i), "ship0")) model = i;
    elite_session_state state, before;
    elite_controls_input input = {0};
    CHECK(elite_session_size() == 1124);
    CHECK(!elite_session_valid(NULL));
    CHECK(elite_session_reset(NULL, model) == ELITE_SESSION_INVALID);
    CHECK(elite_session_reset(&state, model) == ELITE_SESSION_OK);
    CHECK(elite_session_valid(&state) && state.station.docked == 1);
    CHECK(state.scene.profile == state.station.commander.ship);
    before = state;
    CHECK(elite_session_reset(&state, UINT32_MAX) == ELITE_SESSION_INVALID);
    CHECK(!memcmp(&state, &before, sizeof state));
    CHECK(elite_session_return_test(&state) == ELITE_SESSION_WRONG_PHASE);
    CHECK(elite_session_step(&state, &input, 1) == ELITE_SESSION_WRONG_PHASE);
    CHECK(elite_session_reset_flight(&state) == ELITE_SESSION_WRONG_PHASE);
    CHECK(!memcmp(&state, &before, sizeof state));
    for (unsigned cycle = 0; cycle < 100; ++cycle) {
        CHECK(elite_session_trade(&state, 0, 1) == ELITE_TRADE_OK);
        CHECK(state.station.commander.cash == 4964 && state.station.commander.cargo[0] == 1);
        elite_station_state station = state.station;
        CHECK(elite_session_launch(&state) == ELITE_SESSION_OK);
        CHECK(state.station.docked == 0 && state.scene.ticks == 0);
        before = state;
        CHECK(elite_session_launch(&state) == ELITE_SESSION_WRONG_PHASE);
        CHECK(!memcmp(&state, &before, sizeof state));
        CHECK(elite_session_trade(&state, 0, 1) == ELITE_TRADE_NOT_DOCKED);
        before.station.last_result = ELITE_TRADE_NOT_DOCKED;
        CHECK(!memcmp(&state, &before, sizeof state));
        elite_scene_state scene = state.scene;
        for (unsigned tick = 0; tick < 60; ++tick) {
            input.accel = tick < 30 ? 1 : -1;
            input.tilt_roll = tick < 30 ? 20 : -20;
            input.tilt_pitch = tick < 30 ? 5 : -5;
            CHECK(elite_scene_step(&scene, &input, 1) == ELITE_FLIGHT_OK);
            CHECK(elite_session_step(&state, &input, 1) == ELITE_SESSION_OK);
            CHECK(!memcmp(&state.scene, &scene, sizeof scene));
            CHECK(!memcmp(&state.station.commander, &station.commander, sizeof(commander_t)));
            CHECK(!memcmp(state.station.prices, station.prices, sizeof station.prices));
        }
        before = state;
        input.tilt_roll = INT32_MIN;
        CHECK(elite_session_step(&state, &input, 1) == ELITE_SESSION_INVALID);
        CHECK(!memcmp(&state, &before, sizeof state));
        input.tilt_roll = 0;
        CHECK(elite_session_step(&state, &input, 5) == ELITE_SESSION_INVALID);
        CHECK(elite_session_step(&state, NULL, 1) == ELITE_SESSION_INVALID);
        CHECK(!memcmp(&state, &before, sizeof state));
        CHECK(elite_session_reset_flight(&state) == ELITE_SESSION_OK && state.scene.ticks == 0);
        CHECK(!memcmp(&state.station, &before.station, sizeof state.station));
        CHECK(elite_session_return_test(&state) == ELITE_SESSION_OK);
        station.last_result = ELITE_TRADE_NOT_DOCKED;
        CHECK(!memcmp(&state.station, &station, sizeof station));
        CHECK(elite_session_trade(&state, 0, 0) == ELITE_TRADE_OK);
        CHECK(state.station.commander.cash == 5000 && state.station.commander.cargo[0] == 0);
    }
    before = state;
    state.magic = 0; CHECK(!elite_session_valid(&state)); state = before;
    state.version = 2; CHECK(!elite_session_valid(&state)); state = before;
    state.station.docked = 2; CHECK(!elite_session_valid(&state)); state = before;
    state.scene.profile = 1; CHECK(!elite_session_valid(&state)); state = before;
    state.scene.stars[0].x = INT32_MIN; CHECK(!elite_session_valid(&state)); state = before;
    memset(state.scene.target.rotmat, 0, sizeof state.scene.target.rotmat);
    CHECK(!elite_session_valid(&state)); state = before;
    state.station.prices[0] = 3; CHECK(!elite_session_valid(&state)); state = before;
    state.station.commander.ship = 255; CHECK(!elite_session_valid(&state)); state = before;
    CHECK(elite_session_valid(&state));
    puts("OK: session layout, 100 trade/launch/60-tick/return/sell cycles, atomic failures and corrupt-state guards");
    return 0;
}
