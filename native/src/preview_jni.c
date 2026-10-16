#include <jni.h>
#include "elite_mesh.h"
#include "elite_scene.h"
#include "elite_station.h"

static jbyteArray station_bytes(JNIEnv *env, const elite_station_state *state) {
    jbyteArray output = (*env)->NewByteArray(env, (jsize)sizeof(*state));
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize)sizeof(*state), (const jbyte *)state);
    return output;
}

JNIEXPORT jbyteArray JNICALL Java_elite_preview_NativeStation_reset(JNIEnv *env, jclass type) {
    (void)type;
    elite_station_state state;
    if (elite_station_reset(&state)) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (error) (*env)->ThrowNew(env, error, "Could not initialize station snapshot");
        return NULL;
    }
    return station_bytes(env, &state);
}

JNIEXPORT jbyteArray JNICALL Java_elite_preview_NativeStation_trade(JNIEnv *env, jclass type,
        jbyteArray snapshot, jint item, jboolean buy) {
    (void)type;
    if (!snapshot || (*env)->GetArrayLength(env, snapshot) != (jsize)sizeof(elite_station_state)) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (error) (*env)->ThrowNew(env, error, "Invalid station snapshot length");
        return NULL;
    }
    elite_station_state state;
    (*env)->GetByteArrayRegion(env, snapshot, 0, (jsize)sizeof(state), (jbyte *)&state);
    if ((*env)->ExceptionCheck(env)) return NULL;
    elite_station_trade(&state, (uint32_t)item, buy == JNI_TRUE);
    return station_bytes(env, &state);
}

static jbyteArray scene_bytes(JNIEnv *env, const elite_scene_state *scene) {
    jbyteArray output = (*env)->NewByteArray(env, (jsize)sizeof(*scene));
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize)sizeof(*scene), (const jbyte *)scene);
    return output;
}

JNIEXPORT jbyteArray JNICALL Java_elite_preview_NativeFlight_reset(JNIEnv *env, jclass type, jint model, jint profile) {
    (void)type;
    elite_scene_state scene;
    if (elite_scene_reset(&scene, (uint32_t)model, (uint32_t)profile) != ELITE_FLIGHT_OK) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (error) (*env)->ThrowNew(env, error, "Invalid scene model/profile");
        return NULL;
    }
    return scene_bytes(env, &scene);
}

JNIEXPORT jbyteArray JNICALL Java_elite_preview_NativeFlight_step(JNIEnv *env, jclass type,
        jbyteArray input_state, jintArray input, jint ticks) {
    (void)type;
    if (!input_state || !input || (*env)->GetArrayLength(env, input_state) != (jsize)sizeof(elite_scene_state) ||
        (*env)->GetArrayLength(env, input) != 9 || ticks < 0 || ticks > 4) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (error) (*env)->ThrowNew(env, error, "Invalid flight snapshot/input");
        return NULL;
    }
    elite_scene_state scene;
    jint values[9];
    (*env)->GetByteArrayRegion(env, input_state, 0, (jsize)sizeof(scene), (jbyte *)&scene);
    (*env)->GetIntArrayRegion(env, input, 0, 9, values);
    if ((*env)->ExceptionCheck(env)) return NULL;
    elite_controls_input controls = {
        values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]
    };
    if (elite_scene_step(&scene, &controls, (uint32_t)ticks) != ELITE_FLIGHT_OK) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (error) (*env)->ThrowNew(env, error, "Native flight scene rejected input or state");
        return NULL;
    }
    return scene_bytes(env, &scene);
}

JNIEXPORT jint JNICALL Java_elite_preview_NativeMesh_recommendedDistance(JNIEnv *env, jclass type, jint index) {
    (void)env; (void)type;
    const shipheader_t *model = elite_model_at((uint32_t)index);
    if (!model) return 360;
    int radius = 0;
    for (unsigned i = 0; i < model->vertex_count; ++i) {
        const vertex_t *v = &model->vertices[i];
        int x = v->x < 0 ? -v->x : v->x;
        int y = v->y < 0 ? -v->y : v->y;
        int z = v->z < 0 ? -v->z : v->z;
        if (x + y + z > radius) radius = x + y + z;
    }
    int distance = radius * 4;
    if (distance < 256) distance = 256;
    if (distance > model->size * 256) distance = model->size * 256;
    return distance;
}

JNIEXPORT jobjectArray JNICALL Java_elite_preview_NativeMesh_modelNames(JNIEnv *env, jclass type) {
    (void)type;
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (!string_class) return NULL;
    uint32_t count = elite_model_count();
    jobjectArray names = (*env)->NewObjectArray(env, (jsize)count, string_class, NULL);
    (*env)->DeleteLocalRef(env, string_class);
    if (!names) return NULL;
    for (uint32_t i = 0; i < count; ++i) {
        jstring name = (*env)->NewStringUTF(env, elite_model_name(i));
        if (!name) return NULL;
        (*env)->SetObjectArrayElement(env, names, (jsize)i, name);
        (*env)->DeleteLocalRef(env, name);
        if ((*env)->ExceptionCheck(env)) return NULL;
    }
    return names;
}

JNIEXPORT jintArray JNICALL Java_elite_preview_NativeMesh_buildFrame(
        JNIEnv *env, jclass type, jint model, jintArray input_pose, jint flags) {
    (void)type;
    if (!input_pose || (*env)->GetArrayLength(env, input_pose) != 12) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (error) (*env)->ThrowNew(env, error, "Pose requires nine rotation and three location int32 values");
        return NULL;
    }
    jint pose[12];
    (*env)->GetIntArrayRegion(env, input_pose, 0, 12, pose);
    if ((*env)->ExceptionCheck(env)) return NULL;
    stellarobject_t object = {0};
    for (unsigned i = 0; i < 3; ++i) {
        object.rotmat[i].x = pose[i * 3];
        object.rotmat[i].y = pose[i * 3 + 1];
        object.rotmat[i].z = pose[i * 3 + 2];
    }
    object.location.x = pose[9]; object.location.y = pose[10]; object.location.z = pose[11];
    object.type = 1;
    elite_mesh_frame frame;
    int status = elite_mesh_build((uint32_t)model, &object, (uint32_t)flags, &frame);
    jint data[4 + 3 * ELITE_MESH_CAPACITY + (2 + ELITE_MESH_CAPACITY) * ELITE_MESH_CAPACITY];
    unsigned used = 4;
    data[0] = 1; data[1] = status;
    data[2] = status == ELITE_MESH_OK ? (jint)frame.vertex_count : 0;
    data[3] = status == ELITE_MESH_OK ? (jint)frame.command_count : 0;
    if (status == ELITE_MESH_OK) {
        for (unsigned i = 0; i < frame.vertex_count; ++i)
            for (unsigned j = 0; j < 3; ++j) data[used++] = frame.vertices[i][j];
        for (unsigned i = 0; i < frame.command_count; ++i) {
            const elite_mesh_command *command = &frame.commands[i];
            data[used++] = command->count; data[used++] = command->colour;
            for (unsigned j = 0; j < command->count; ++j) data[used++] = command->indices[j];
        }
    }
    jintArray output = (*env)->NewIntArray(env, (jsize)used);
    if (output) (*env)->SetIntArrayRegion(env, output, 0, (jsize)used, data);
    return output;
}
