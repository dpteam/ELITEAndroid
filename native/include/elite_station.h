#ifndef ELITE_STATION_H
#define ELITE_STATION_H
#include "elite_flight.h"
#include "commander.h"

#define ELITE_STATION_MAGIC 0x4553544eu
#define ELITE_GOODS_COUNT 17u

typedef struct {
    uint8_t base_price, factor_type, base_amount, mask;
} elite_price_control;
extern const elite_price_control market_control[17];
extern const char elite_name_pairs[];

typedef struct {
    uint32_t magic, version;
    commander_t commander;
    seed_t system;
    planetinfo_t info;
    uint16_t system_index;
    uint32_t prices[17];
    char system_name[12];
    uint32_t docked;
    uint32_t last_result;
} elite_station_state;

enum elite_trade_result {
    ELITE_TRADE_OK = 0, ELITE_TRADE_INVALID = 1, ELITE_TRADE_NOT_DOCKED = 2,
    ELITE_TRADE_NO_STOCK = 3, ELITE_TRADE_FULL = 4, ELITE_TRADE_NO_CASH = 5,
    ELITE_TRADE_NO_CARGO = 6, ELITE_TRADE_OVERFLOW = 7
};

ELITE_API uint32_t elite_station_size(void);
ELITE_API int elite_system_seed(const seed_t *galaxy, uint32_t index, seed_t *system);
ELITE_API int elite_nearest_system(const seed_t *galaxy, uint32_t x, uint32_t y, seed_t *system);
ELITE_API int elite_system_info(const seed_t *system, uint32_t from_x, uint32_t from_y, planetinfo_t *info);
ELITE_API int elite_system_name(const seed_t *system, char name[12]);
ELITE_API int elite_market_generate(uint32_t economy, uint32_t random,
                                   uint32_t prices[17], uint8_t stock[17]);
ELITE_API uint32_t elite_cargo_tonnes(const commander_t *commander);
ELITE_API uint32_t elite_cargo_illegal(const commander_t *commander);
ELITE_API int elite_station_reset(elite_station_state *state);
/* One-unit trade, no sounds or UI callbacks. On failure, only last_result
 * changes. Extra overflow guards intentionally differ from old uint8 wrap. */
ELITE_API int elite_station_trade(elite_station_state *state, uint32_t item, int buy);
#endif
