#ifndef ELITE_SESSION_H
#define ELITE_SESSION_H
#include "elite_scene.h"
#include "elite_station.h"
#include "elite_navigation.h"

#define ELITE_SESSION_MAGIC 0x45534553u
#define ELITE_DOCK_DISTANCE 320u

/* New integration harness, NOT a recovered game/save format. One owner for
 * commander/market, bounded scene and controlled docking corridor. */
typedef struct {
    uint32_t distance, speed, alignment, ticks, status, reserved;
} elite_docking_state;

enum { ELITE_DOCK_APPROACH = 0, ELITE_DOCK_READY = 1, ELITE_DOCK_DOCKED = 2 };

typedef struct {
    uint32_t magic, version;
    elite_station_state station;
    elite_scene_state scene;
    elite_navigation_state navigation;
    elite_docking_state docking;
} elite_session_state;

enum {
    ELITE_SESSION_OK = 0, ELITE_SESSION_INVALID = 1,
    ELITE_SESSION_WRONG_PHASE = 2, ELITE_SESSION_NOT_READY = 3
};
ELITE_API uint32_t elite_session_size(void);
ELITE_API int elite_session_valid(const elite_session_state *state);
ELITE_API int elite_session_reset(elite_session_state *state, uint32_t target_model);
ELITE_API int elite_session_select_target(elite_session_state *state, uint32_t index);
ELITE_API int elite_session_select_reachable(elite_session_state *state);
ELITE_API int elite_session_jump(elite_session_state *state);
ELITE_API int elite_session_approach(elite_session_state *state, uint32_t ticks);
ELITE_API int elite_session_dock(elite_session_state *state);
ELITE_API int elite_session_launch(elite_session_state *state);
/* Legacy compatibility shortcut retained for comparison; the UI now exposes
 * the bounded approach/dock path separately. */
ELITE_API int elite_session_return_test(elite_session_state *state);
ELITE_API int elite_session_reset_flight(elite_session_state *state);
ELITE_API int elite_session_step(elite_session_state *state, const elite_controls_input *input, uint32_t ticks);
/* Uses ELITE_TRADE_* results. Failed trades change only station.last_result. */
ELITE_API int elite_session_trade(elite_session_state *state, uint32_t item, int buy);
#endif
