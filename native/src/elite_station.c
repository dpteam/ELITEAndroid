/* Android station-state subset, reconciled with APK market/navigation code.
 * This is not the original UI, full station lifecycle, mission or save module.
 */
#include "elite_station.h"
#include <stddef.h>
#include <string.h>

_Static_assert(sizeof(seed_t) == 6 && sizeof(planetinfo_t) == 8, "Planet ABI drift");
_Static_assert(sizeof(commander_t) == 92, "Commander ABI drift");
_Static_assert(offsetof(commander_t, cash) == 8 && offsetof(commander_t, cargo_space) == 48,
               "Commander credit/cargo offsets");
_Static_assert(offsetof(commander_t, gal_seed) == 33 && offsetof(commander_t, planet_random) == 40,
               "Commander seed offsets");
_Static_assert(offsetof(commander_t, cargo) == 50 && offsetof(commander_t, station_cargo) == 67,
               "Commander market offsets");
_Static_assert(offsetof(elite_station_state, prices) == 116 && sizeof(elite_station_state) == 204,
               "Station snapshot ABI drift");

uint32_t elite_station_size(void) { return sizeof(elite_station_state); }
static int absolute(int x) { return x < 0 ? -x : x; }
static void churn(seed_t *seed) { elite_churn_seed((uint8_t *)seed); }

int elite_system_seed(const seed_t *galaxy, uint32_t index, seed_t *system) {
    if (!galaxy || !system || index > 255) return -1;
    seed_t value = *galaxy;
    for (uint32_t i = 0; i < index * 4; ++i) churn(&value);
    *system = value;
    return 0;
}

int elite_nearest_system(const seed_t *galaxy, uint32_t x, uint32_t y, seed_t *system) {
    if (!galaxy || !system || x > 255 || y > 255) return -1;
    seed_t value = *galaxy, found = {0};
    int best = 255, index = -1;
    for (int i = 0; i < 256; ++i) {
        int distance = absolute((int)value.s3 - (int)x) + absolute((int)value.s1 - (int)y);
        if (distance < best) { best = distance; index = i; found = value; }
        for (unsigned j = 0; j < 4; ++j) churn(&value);
    }
    // Original leaves caller bytes unchanged if no distance beats 255.
    if (index >= 0) *system = found;
    return index;
}

int elite_system_info(const seed_t *system, uint32_t from_x, uint32_t from_y, planetinfo_t *info) {
    if (!system || !info || from_x > 255 || from_y > 255) return -1;
    planetinfo_t value = {0};
    int dx = absolute((int)system->s3 - (int)from_x);
    int dy = absolute((int)system->s1 - (int)from_y) / 2;
    value.distance = (uint16_t)(elite_sqrt_u32((uint32_t)(dx * dx + dy * dy)) * 4);
    value.economy = system->s1 & 7;
    value.gov = (system->s2 / 8) & 7;
    if (value.gov < 2) value.economy |= 2;
    value.tech = (value.economy ^ 7) + (system->s3 & 3) + value.gov / 2 + (value.gov & 1);
    value.population = value.tech * 4 + value.economy + value.gov + 1;
    value.gross = (uint16_t)(8 * value.population * (3 + (value.economy ^ 7)) * (4 + value.gov));
    *info = value;
    return 0;
}

int elite_system_name(const seed_t *system, char name[12]) {
    if (!system || !name) return -1;
    seed_t value = *system;
    unsigned pairs = value.s0 & 0x40 ? 4 : 3;
    unsigned count = 0;
    memset(name, 0, 12);
    for (unsigned i = 0; i < pairs; ++i) {
        unsigned pair = value.s5 & 31;
        if (pair) {
            name[count++] = elite_name_pairs[26 + pair * 2];
            char second = elite_name_pairs[27 + pair * 2];
            if (second != '?') name[count++] = second;
        }
        churn(&value);
    }
    return (int)count;
}

int elite_market_generate(uint32_t economy, uint32_t random, uint32_t prices[17], uint8_t stock[17]) {
    if (economy > 7 || random > 255 || !prices || !stock) return -1;
    for (unsigned i = 0; i < 17; ++i) {
        const elite_price_control *control = &market_control[i];
        int factor = (int)economy * (control->factor_type & 31);
        if (control->factor_type & 128) factor = -factor;
        int fluctuation = control->mask & random;
        prices[i] = ((factor + control->base_price + fluctuation) & 255) * 4;
        // APK treats base_amount as signed and tests the full negative sum,
        // unlike the older GBA stock&0x80 check. Alien stock is always zero.
        int quantity = (int8_t)control->base_amount + fluctuation - factor;
        stock[i] = i == 16 || quantity < 0 ? 0 : (uint8_t)(quantity & 63);
    }
    return 0;
}

uint32_t elite_cargo_tonnes(const commander_t *commander) {
    if (!commander) return 0;
    uint32_t count = commander->cargo[16];
    for (unsigned i = 0; i < 13; ++i) count += commander->cargo[i];
    return count;
}
uint32_t elite_cargo_illegal(const commander_t *commander) {
    if (!commander) return 0;
    return (commander->cargo[3] + commander->cargo[6]) * 2u + commander->cargo[10];
}

int elite_station_reset(elite_station_state *state) {
    if (!state) return -1;
    memset(state, 0, sizeof(*state));
    state->magic = ELITE_STATION_MAGIC; state->version = 1;
    state->commander = default_commander;
    int index = elite_nearest_system(&state->commander.gal_seed,
        state->commander.cpl_coord.x, state->commander.cpl_coord.y, &state->system);
    if (index < 0) return -1;
    state->system_index = (uint16_t)index;
    state->commander.cpl_coord.x = state->system.s3;
    state->commander.cpl_coord.y = state->system.s1;
    elite_system_info(&state->system, state->system.s3, state->system.s1, &state->info);
    elite_system_name(&state->system, state->system_name);
    elite_market_generate(state->info.economy, state->commander.planet_random,
                          state->prices, state->commander.station_cargo);
    state->docked = 1;
    return 0;
}

static int result(elite_station_state *state, enum elite_trade_result value) {
    state->last_result = value;
    return value;
}

int elite_station_trade(elite_station_state *state, uint32_t item, int buy) {
    if (!state) return ELITE_TRADE_INVALID;
    if (state->magic != ELITE_STATION_MAGIC || state->version != 1 ||
        item >= 17 || (buy != 0 && buy != 1) || state->commander.ship >= 15)
        return result(state, ELITE_TRADE_INVALID);
    if (state->docked != 1) return result(state, ELITE_TRADE_NOT_DOCKED);
    commander_t *commander = &state->commander;
    uint32_t price = state->prices[item];
    if (price > 1020 || price % 4) return result(state, ELITE_TRADE_INVALID);
    if (buy) {
        if (!commander->station_cargo[item]) return result(state, ELITE_TRADE_NO_STOCK);
        if (elite_cargo_tonnes(commander) >= commander->cargo_space) return result(state, ELITE_TRADE_FULL);
        if (commander->cash < price) return result(state, ELITE_TRADE_NO_CASH);
        if (commander->cargo[item] == 255) return result(state, ELITE_TRADE_OVERFLOW);
        ++commander->cargo[item]; --commander->station_cargo[item]; commander->cash -= price;
    } else {
        if (!commander->cargo[item]) return result(state, ELITE_TRADE_NO_CARGO);
        if (commander->station_cargo[item] == 255 || UINT32_MAX - commander->cash < price)
            return result(state, ELITE_TRADE_OVERFLOW);
        --commander->cargo[item]; ++commander->station_cargo[item]; commander->cash += price;
    }
    return result(state, ELITE_TRADE_OK);
}
