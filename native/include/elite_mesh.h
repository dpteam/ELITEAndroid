#ifndef ELITE_MESH_H
#define ELITE_MESH_H
#include "elite_core.h"

#define ELITE_MESH_CAPACITY 64
#define ELITE_MESH_SOLID 1u
#define ELITE_MESH_SHADED 2u

enum elite_mesh_status {
    ELITE_MESH_OK = 0,
    ELITE_MESH_CULLED = 1,
    ELITE_MESH_DISTANT = 2,
    ELITE_MESH_INVALID = 3,
    ELITE_MESH_EFFECT_UNSUPPORTED = 4
};

typedef struct {
    uint8_t count;
    uint8_t colour;
    uint8_t indices[ELITE_MESH_CAPACITY];
} elite_mesh_command;

/* Pointer-free, fixed-width frame: identical layout on all supported ABIs.
 * Camera coordinates are x,y,-z as in the APK's g_crds short[64][3].
 * Commands with 2 vertices are lines; 3+ vertices are GL triangle fans.
 */
typedef struct {
    uint32_t status;
    uint32_t vertex_count;
    uint32_t command_count;
    int16_t vertices[ELITE_MESH_CAPACITY][3];
    uint8_t face_visibility[16];
    elite_mesh_command commands[ELITE_MESH_CAPACITY];
} elite_mesh_frame;

ELITE_API uint32_t elite_model_count(void);
ELITE_API const char *elite_model_name(uint32_t index);
const shipheader_t *elite_model_at(uint32_t index);
/* Reentrant mesh preparation, NOT the full game entry(): no lasers,
 * explosions, distant-object flicker, sprites, HUD or game-state mutation.
 * These unsupported effects return an explicit status, not fake geometry.
 */
ELITE_API int elite_mesh_build(uint32_t model, const stellarobject_t *object,
                             uint32_t flags, elite_mesh_frame *frame);
ELITE_API uint32_t elite_mesh_frame_size(void);
#endif
