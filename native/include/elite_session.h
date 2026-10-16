#ifndef ELITE_SESSION_H
#define ELITE_SESSION_H
#include "elite_scene.h"
#include "elite_station.h"

#define ELITE_SESSION_MAGIC 0x45534553u

/* New integration harness, NOT a recovered game/save format. One owner for
 * commander/market and the bounded scene. station.docked is the sole phase. */
typedef struct {
    uint32_t magic, version;
    elite_station_state station;
    elite_scene_state scene;
} elite_session_state;

enum { ELITE_SESSION_OK = 0, ELITE_SESSION_INVALID = 1, ELITE_SESSION_WRONG_PHASE = 2 };
ELITE_API uint32_t elite_session_size(void);
ELITE_API int elite_session_valid(const elite_session_state *state);
ELITE_API int elite_session_reset(elite_session_state *state, uint32_t target_model);
ELITE_API int elite_session_launch(elite_session_state *state);
/* Explicit test shortcut: no approach geometry, collision, docking rewards,
 * legal-status changes or market regeneration are claimed. Same station. */
ELITE_API int elite_session_return_test(elite_session_state *state);
ELITE_API int elite_session_reset_flight(elite_session_state *state);
ELITE_API int elite_session_step(elite_session_state *state, const elite_controls_input *input, uint32_t ticks);
/* Uses ELITE_TRADE_* results. Failed trades change only station.last_result. */
ELITE_API int elite_session_trade(elite_session_state *state, uint32_t item, int buy);
#endif
