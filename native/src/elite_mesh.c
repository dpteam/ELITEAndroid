/* Mesh preparation reconstructed from Elite AGB motion.c entry(), with the
 * Android g_crds / polygon3d path corroborated against the supplied APK.
 * See native/ORIGIN.md. Rendering effects and game mutations are NOT emulated.
 */
#include "elite_mesh.h"
#include <stddef.h>
#include <string.h>

_Static_assert(sizeof(elite_mesh_command) == 66, "Mesh command wire layout");
_Static_assert(offsetof(elite_mesh_frame, commands) == 412, "Mesh frame wire layout");
_Static_assert(sizeof(elite_mesh_frame) == 4636, "Mesh frame ABI drift");

static int32_t magnitude(int32_t n) { return n < 0 ? -n : n; }
static int32_t maximum3(vector32_t v) {
    int32_t n = magnitude(v.x);
    if (magnitude(v.y) > n) n = magnitude(v.y);
    if (magnitude(v.z) > n) n = magnitude(v.z);
    return n;
}
static int16_t clamp_short(int32_t n) {
    return (int16_t)(n < -32768 ? -32768 : n > 32767 ? 32767 : n);
}
static int finish(elite_mesh_frame *frame, enum elite_mesh_status status) {
    frame->status = status;
    return (int)status;
}

uint32_t elite_mesh_frame_size(void) { return sizeof(elite_mesh_frame); }

int elite_mesh_build(uint32_t model, const stellarobject_t *object,
                     uint32_t flags, elite_mesh_frame *frame) {
    if (!frame) return ELITE_MESH_INVALID;
    memset(frame, 0, sizeof(*frame));
    const shipheader_t *data = elite_model_at(model);
    if (!data || !object || (flags & ~(ELITE_MESH_SOLID | ELITE_MESH_SHADED)))
        return finish(frame, ELITE_MESH_INVALID);
    if (!data->vertices || !data->faces || data->vertex_count > 64 || data->face_count > 15 ||
        data->poly_count > 64 || data->line_count > 64 ||
        (data->line_count && !data->lines) || (data->poly_count && !data->polys))
        return finish(frame, ELITE_MESH_INVALID);
    /* Keep the original bounded fixed-point domain; reject values which would
       overflow legacy int32 operations or make a singular camera vector hang. */
    for (unsigned r = 0; r < 3; ++r) {
        // Motion can temporarily exceed the nominal 0x6000 unit between tidy()
        // calls. Accept that bounded drift rather than blanking valid poses.
        if (object->rotmat[r].x < -0x8000 || object->rotmat[r].x > 0x8000 ||
            object->rotmat[r].y < -0x8000 || object->rotmat[r].y > 0x8000 ||
            object->rotmat[r].z < -0x8000 || object->rotmat[r].z > 0x8000)
            return finish(frame, ELITE_MESH_INVALID);
    }
    const vector32_t loc = object->location;
    if (loc.x < -0x100000 || loc.x > 0x100000 || loc.y < -0x100000 || loc.y > 0x100000 ||
        loc.z < -0x100000 || loc.z > 0x100000) return finish(frame, ELITE_MESH_INVALID);
    if (object->flag & 0x80) return finish(frame, ELITE_MESH_CULLED);
    if (object->missiles & (0x80 | 0x20 | 0x40))
        return finish(frame, ELITE_MESH_EFFECT_UNSUPPORTED);
    if (loc.z < 0 || loc.z > 0xc000 || magnitude(loc.x) > loc.z || magnitude(loc.y) > loc.z)
        return finish(frame, ELITE_MESH_CULLED);
    if (data->size < magnitude(loc.z / 256)) return finish(frame, ELITE_MESH_DISTANT);

    int vp = magnitude(loc.z / 8);
    vp = vp < 0x1ff ? vp / 16 : 0x1f;
    matrix_t matrix;
    for (unsigned r = 0; r < 3; ++r) {
        matrix[r].x = (object->rotmat[r].x * 2) / 0xc5;
        matrix[r].y = (object->rotmat[r].y * 2) / 0xc5;
        matrix[r].z = (object->rotmat[r].z * 2) / 0xc5;
    }
    vector32_t loc8 = loc;
    int floater = data->shift;
    do {
        loc8.x /= 2; loc8.y /= 2; loc8.z /= 2; ++floater;
    } while (maximum3(loc8) > 255);
    vector32_t camera;
    dot_product(&camera, &loc8, matrix);
    if (maximum3(camera) == 0) return finish(frame, ELITE_MESH_INVALID);
    while (maximum3(camera) < 64) {
        camera.x *= 2; camera.y *= 2; camera.z *= 2;
    }
    frame->face_visibility[15] = 255;
    for (unsigned i = 0; i < data->face_count; ++i) {
        const face_t *face = &data->faces[i];
        if (face->vanishpoint < vp) continue;
        if (floater < 4) {
            int pv = floater;
            for (;;) {
                int x = face->x, y = face->y, z = face->z;
                for (int j = 0; j < pv; ++j) { x /= 2; y /= 2; z /= 2; }
                loc8.x = x + camera.x; loc8.y = y + camera.y; loc8.z = z + camera.z;
                if (maximum3(loc8) <= 255) break;
                camera.x /= 2; camera.y /= 2; camera.z /= 2;
                pv = 1;
            }
        } else loc8 = camera;
        int angle = (loc8.x * face->x) / 256 + (loc8.y * face->y) / 256 +
                    (loc8.z * face->z) / 256;
        frame->face_visibility[i] = (uint8_t)(angle < 0 ? -angle : 0);
    }
    int32_t tmp = matrix[0].y; matrix[0].y = matrix[1].x; matrix[1].x = tmp;
    tmp = matrix[0].z; matrix[0].z = matrix[2].x; matrix[2].x = tmp;
    tmp = matrix[1].z; matrix[1].z = matrix[2].y; matrix[2].y = tmp;
    frame->vertex_count = data->vertex_count;
    for (unsigned i = 0; i < data->vertex_count; ++i) {
        const vertex_t *v = &data->vertices[i];
        if (v->vanishpoint < vp && !(flags & ELITE_MESH_SOLID)) continue;
        int visible = 0;
        for (unsigned j = 0; j < 4; ++j) {
            if (v->faces[j] > 15) return finish(frame, ELITE_MESH_INVALID);
            visible |= frame->face_visibility[v->faces[j]];
        }
        if (!visible) continue;
        vector32_t vertex = {v->x, v->y, v->z};
        dot_product(&camera, &vertex, matrix);
        frame->vertices[i][0] = clamp_short(loc.x + camera.x);
        frame->vertices[i][1] = clamp_short(loc.y + camera.y);
        /* The old path negates a saturated short, including -32768 wrap. */
        frame->vertices[i][2] = (int16_t)(-(int32_t)clamp_short(loc.z + camera.z));
    }
    int solid = (flags & ELITE_MESH_SOLID) && data->polys;
    unsigned total = solid ? data->poly_count : data->line_count;
    for (unsigned i = 0; i < total; ++i) {
        unsigned count, colour;
        const uint8_t *indices;
        if (solid) {
            const polygon_t *poly = &data->polys[i];
            if (poly->face > 15) return finish(frame, ELITE_MESH_INVALID);
            if (!frame->face_visibility[poly->face] || poly->vanishpoint < vp) continue;
            count = poly->count; colour = poly->colour; indices = poly->verts;
            if ((flags & ELITE_MESH_SHADED) && colour) {
                int value = 1 + (colour & 3) + (int8_t)frame->face_visibility[poly->face] / 2;
                colour = 16 - (value > 15 ? 15 : value);
            }
        } else {
            const line_t *line = &data->lines[i];
            if (line->faces[0] > 15 || line->faces[1] > 15)
                return finish(frame, ELITE_MESH_INVALID);
            if (line->vanishpoint < vp || !(frame->face_visibility[line->faces[0]] ||
                                           frame->face_visibility[line->faces[1]])) continue;
            count = 2; colour = 4; indices = line->vertices;
        }
        if (!count) continue;
        if (count > 64 || !indices || frame->command_count >= 64)
            return finish(frame, ELITE_MESH_INVALID);
        elite_mesh_command *command = &frame->commands[frame->command_count++];
        command->count = (uint8_t)count; command->colour = (uint8_t)colour;
        for (unsigned j = 0; j < count; ++j) {
            if (indices[j] >= frame->vertex_count) return finish(frame, ELITE_MESH_INVALID);
            command->indices[j] = indices[j];
        }
    }
    return finish(frame, ELITE_MESH_OK);
}
