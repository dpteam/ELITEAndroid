#ifndef ELITE_NAVIGATION_H
#define ELITE_NAVIGATION_H
#include "elite_station.h"

typedef struct {
    uint32_t target_index;
    seed_t target;
    planetinfo_t info;
    char name[12];
    uint32_t distance;
} elite_navigation_state;

enum {
    ELITE_NAV_OK = 0, ELITE_NAV_INVALID = 1, ELITE_NAV_NO_TARGET = 2,
    ELITE_NAV_NO_FUEL = 3, ELITE_NAV_SAME_SYSTEM = 4
};

ELITE_API uint32_t elite_navigation_size(void);
ELITE_API int elite_navigation_select(const commander_t *commander, uint32_t index,
                                      elite_navigation_state *navigation);
ELITE_API int elite_navigation_find_reachable(const commander_t *commander,
                                               elite_navigation_state *navigation);
/* New bounded navigation composition: consumes fuel and regenerates the target
 * market deterministically. It does not perform original hyperspace countdown,
 * miss-jump, legal-status, encounter, audio or universe effects. */
ELITE_API int elite_navigation_jump(elite_station_state *station,
                                    const elite_navigation_state *navigation);
#endif
