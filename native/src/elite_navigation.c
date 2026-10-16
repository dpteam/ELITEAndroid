#include "elite_navigation.h"
#include <stddef.h>
#include <string.h>

_Static_assert(sizeof(elite_navigation_state) == 36, "Navigation ABI drift");
uint32_t elite_navigation_size(void) { return sizeof(elite_navigation_state); }

int elite_navigation_select(const commander_t *commander, uint32_t index,
                            elite_navigation_state *navigation) {
    if (!commander || !navigation || index > 255) return ELITE_NAV_INVALID;
    elite_navigation_state next = {0};
    if (elite_system_seed(&commander->gal_seed, index, &next.target)) return ELITE_NAV_INVALID;
    if (elite_system_info(&next.target, commander->cpl_coord.x, commander->cpl_coord.y, &next.info) ||
        elite_system_name(&next.target, next.name) < 0) return ELITE_NAV_INVALID;
    next.target_index = index;
    next.distance = next.info.distance;
    *navigation = next;
    return next.distance == 0 ? ELITE_NAV_SAME_SYSTEM : ELITE_NAV_OK;
}

int elite_navigation_find_reachable(const commander_t *commander,
                                    elite_navigation_state *navigation) {
    if (!commander || !navigation) return ELITE_NAV_INVALID;
    for (uint32_t index = 0; index < 256; ++index) {
        int result = elite_navigation_select(commander, index, navigation);
        if (result == ELITE_NAV_OK && navigation->distance <= commander->fuel)
            return ELITE_NAV_OK;
    }
    return ELITE_NAV_NO_TARGET;
}

int elite_navigation_jump(elite_station_state *station,
                          const elite_navigation_state *navigation) {
    if (!station || !navigation || station->magic != ELITE_STATION_MAGIC ||
        station->version != 1 || station->docked || navigation->target_index > 255 ||
        navigation->distance == 0) return ELITE_NAV_INVALID;
    if (navigation->distance > station->commander.fuel || navigation->distance > 60)
        return ELITE_NAV_NO_FUEL;
    elite_station_state next = *station;
    next.commander.fuel = (uint8_t)(next.commander.fuel - navigation->distance);
    next.commander.cpl_coord.x = navigation->target.s3;
    next.commander.cpl_coord.y = navigation->target.s1;
    next.commander.planet_random = (uint8_t)(navigation->target.s4 ^ navigation->target.s5);
    next.system = navigation->target;
    next.info = navigation->info;
    next.system_index = (uint16_t)navigation->target_index;
    memcpy(next.system_name, navigation->name, sizeof(next.system_name));
    if (elite_market_generate(next.info.economy, next.commander.planet_random,
                              next.prices, next.commander.station_cargo)) return ELITE_NAV_INVALID;
    next.docked = 0;
    next.last_result = ELITE_TRADE_OK;
    *station = next;
    return ELITE_NAV_OK;
}
