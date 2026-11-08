#include "libretro.h"
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

#define LOG_TAG "LibretroBridgeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void *g_core_handle = NULL;
static ANativeWindow *g_native_window = NULL;
static JavaVM *g_jvm = NULL;
static jobject g_bridge_obj = NULL;
static jmethodID g_audio_callback_mid = NULL;

static char g_system_dir[512] = {0};
static char g_save_dir[512] = {0};
static enum retro_pixel_format g_pixel_format = RETRO_PIXEL_FORMAT_RGB565;
static struct retro_system_av_info g_av_info = {0};
static unsigned g_current_buf_width = 0;
static unsigned g_current_buf_height = 0;
static bool g_button_states[4][16] = {{false}};
static uint8_t *g_rom_data = NULL;
static size_t g_rom_size = 0;

// Function pointers to Libretro core API
static void (*p_retro_init)(void) = NULL;
static void (*p_retro_deinit)(void) = NULL;
static unsigned (*p_retro_api_version)(void) = NULL;
static void (*p_retro_get_system_info)(struct retro_system_info *info) = NULL;
static void (*p_retro_get_system_av_info)(struct retro_system_av_info *info) = NULL;
static void (*p_retro_set_environment)(retro_environment_t) = NULL;
static void (*p_retro_set_video_refresh)(retro_video_refresh_t) = NULL;
static void (*p_retro_set_audio_sample)(retro_audio_sample_t) = NULL;
static void (*p_retro_set_audio_sample_batch)(retro_audio_sample_batch_t) = NULL;
static void (*p_retro_set_input_poll)(retro_input_poll_t) = NULL;
static void (*p_retro_set_input_state)(retro_input_state_t) = NULL;
static bool (*p_retro_load_game)(const struct retro_game_info *game) = NULL;
static void (*p_retro_unload_game)(void) = NULL;
static void (*p_retro_run)(void) = NULL;
static void (*p_retro_reset)(void) = NULL;
static size_t (*p_retro_serialize_size)(void) = NULL;
static bool (*p_retro_serialize)(void *data, size_t size) = NULL;
static bool (*p_retro_unserialize)(const void *data, size_t size) = NULL;
static void *(*p_retro_get_memory_data)(unsigned id) = NULL;
static size_t (*p_retro_get_memory_size)(unsigned id) = NULL;

// Environment callback
static bool core_environment_cb(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **)data = g_system_dir;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const enum retro_pixel_format *fmt = (const enum retro_pixel_format *)data;
            g_pixel_format = *fmt;
            LOGI("Core requested pixel format: %d", (int)g_pixel_format);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *(bool *)data = true;
            return true;

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *var = (struct retro_variable *)data;
            if (var) {
                var->value = NULL;
            }
            return false;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            return true;

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            if (data) *(bool *)data = false;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: {
            if (data) *(unsigned *)data = 0;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_LANGUAGE: {
            if (data) *(unsigned *)data = 0;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE: {
            if (data) *(int *)data = 1 | 2;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            const struct retro_game_geometry *geom = (const struct retro_game_geometry *)data;
            g_av_info.geometry = *geom;
            if (g_native_window) {
                ANativeWindow_setBuffersGeometry(g_native_window, geom->base_width, geom->base_height, WINDOW_FORMAT_RGBA_8888);
            }
            return true;
        }

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            return false;

        default:
            return false;
    }
}

// Video refresh callback: copies emulated pixels directly to ANativeWindow buffer
static void core_video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || !g_native_window || width == 0 || height == 0) return;

    if (g_current_buf_width != width || g_current_buf_height != height) {
        g_current_buf_width = width;
        g_current_buf_height = height;
        ANativeWindow_setBuffersGeometry(g_native_window, width, height, WINDOW_FORMAT_RGBA_8888);
    }

    ANativeWindow_Buffer win_buffer;
    if (ANativeWindow_lock(g_native_window, &win_buffer, NULL) < 0) {
        return;
    }

    uint32_t *dst_row = (uint32_t *)win_buffer.bits;
    int dst_stride = win_buffer.stride;

    if (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
        const uint32_t *src_row = (const uint32_t *)data;
        for (unsigned y = 0; y < height && y < (unsigned)win_buffer.height; y++) {
            for (unsigned x = 0; x < width && x < (unsigned)win_buffer.width; x++) {
                uint32_t pixel = src_row[x];
                // Convert XRGB8888 to RGBA_8888
                uint8_t r = (pixel >> 16) & 0xFF;
                uint8_t g = (pixel >> 8) & 0xFF;
                uint8_t b = pixel & 0xFF;
                dst_row[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
            src_row = (const uint32_t *)((const uint8_t *)src_row + pitch);
            dst_row += dst_stride;
        }
    } else if (g_pixel_format == RETRO_PIXEL_FORMAT_RGB565) {
        const uint16_t *src_row = (const uint16_t *)data;
        for (unsigned y = 0; y < height && y < (unsigned)win_buffer.height; y++) {
            for (unsigned x = 0; x < width && x < (unsigned)win_buffer.width; x++) {
                uint16_t pixel = src_row[x];
                // RGB565 to RGBA_8888
                uint8_t r = ((pixel >> 11) & 0x1F) * 255 / 31;
                uint8_t g = ((pixel >> 5) & 0x3F) * 255 / 63;
                uint8_t b = (pixel & 0x1F) * 255 / 31;
                dst_row[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
            src_row = (const uint16_t *)((const uint8_t *)src_row + pitch);
            dst_row += dst_stride;
        }
    } else if (g_pixel_format == RETRO_PIXEL_FORMAT_0RGB1555) {
        const uint16_t *src_row = (const uint16_t *)data;
        for (unsigned y = 0; y < height && y < (unsigned)win_buffer.height; y++) {
            for (unsigned x = 0; x < width && x < (unsigned)win_buffer.width; x++) {
                uint16_t pixel = src_row[x];
                uint8_t r = ((pixel >> 10) & 0x1F) * 255 / 31;
                uint8_t g = ((pixel >> 5) & 0x1F) * 255 / 31;
                uint8_t b = (pixel & 0x1F) * 255 / 31;
                dst_row[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
            src_row = (const uint16_t *)((const uint8_t *)src_row + pitch);
            dst_row += dst_stride;
        }
    }

    ANativeWindow_unlockAndPost(g_native_window);
}

// Forward declaration
static size_t core_audio_sample_batch_cb(const int16_t *data, size_t frames);

// Audio sample callback (single stereo frame)
static void core_audio_sample_cb(int16_t left, int16_t right) {
    int16_t samples[2] = {left, right};
    core_audio_sample_batch_cb(samples, 1);
}

// Audio batch callback: delivers samples to Java AudioTrack pipeline
static size_t core_audio_sample_batch_cb(const int16_t *data, size_t frames) {
    if (!g_jvm || !g_bridge_obj || !g_audio_callback_mid || frames == 0) return frames;

    JNIEnv *env = nullptr;
    jint res = g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    bool attached = false;
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return frames;
        attached = true;
    }

    jsize sample_count = (jsize)(frames * 2);
    jshortArray arr = env->NewShortArray(sample_count);
    if (arr) {
        env->SetShortArrayRegion(arr, 0, sample_count, (const jshort *)data);
        env->CallVoidMethod(g_bridge_obj, g_audio_callback_mid, arr);
        env->DeleteLocalRef(arr);
    }

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
    return frames;
}

static void core_input_poll_cb(void) {
    // Input polling handled asynchronously via InputRouter
}

static int16_t core_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port < 4 && device == RETRO_DEVICE_JOYPAD && id < 16) {
        return g_button_states[port][id] ? 1 : 0;
    }
    return 0;
}

// =========================================================================
// JNI EXPORT IMPLEMENTATIONS
// =========================================================================

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeInitCore(JNIEnv *env, jobject thiz, jstring coreSoPath, jstring systemDir) {
    const char *so_path = env->GetStringUTFChars(coreSoPath, NULL);
    const char *sys_dir = env->GetStringUTFChars(systemDir, NULL);

    strncpy(g_system_dir, sys_dir, sizeof(g_system_dir) - 1);
    strncpy(g_save_dir, sys_dir, sizeof(g_save_dir) - 1);

    if (g_bridge_obj) {
        env->DeleteGlobalRef(g_bridge_obj);
    }
    g_bridge_obj = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    g_audio_callback_mid = env->GetMethodID(cls, "onNativeAudioSamples", "([S)V");

    LOGI("Cargando libreria core: %s", so_path);
    g_core_handle = dlopen(so_path, RTLD_LAZY | RTLD_LOCAL);
    env->ReleaseStringUTFChars(coreSoPath, so_path);
    env->ReleaseStringUTFChars(systemDir, sys_dir);

    if (!g_core_handle) {
        LOGE("Error en dlopen: %s", dlerror());
        return JNI_FALSE;
    }

    p_retro_init = (void (*)(void))dlsym(g_core_handle, "retro_init");
    p_retro_deinit = (void (*)(void))dlsym(g_core_handle, "retro_deinit");
    p_retro_api_version = (unsigned (*)(void))dlsym(g_core_handle, "retro_api_version");
    p_retro_get_system_info = (void (*)(struct retro_system_info *))dlsym(g_core_handle, "retro_get_system_info");
    p_retro_get_system_av_info = (void (*)(struct retro_system_av_info *))dlsym(g_core_handle, "retro_get_system_av_info");
    p_retro_set_environment = (void (*)(retro_environment_t))dlsym(g_core_handle, "retro_set_environment");
    p_retro_set_video_refresh = (void (*)(retro_video_refresh_t))dlsym(g_core_handle, "retro_set_video_refresh");
    p_retro_set_audio_sample = (void (*)(retro_audio_sample_t))dlsym(g_core_handle, "retro_set_audio_sample");
    p_retro_set_audio_sample_batch = (void (*)(retro_audio_sample_batch_t))dlsym(g_core_handle, "retro_set_audio_sample_batch");
    p_retro_set_input_poll = (void (*)(retro_input_poll_t))dlsym(g_core_handle, "retro_set_input_poll");
    p_retro_set_input_state = (void (*)(retro_input_state_t))dlsym(g_core_handle, "retro_set_input_state");
    p_retro_load_game = (bool (*)(const struct retro_game_info *))dlsym(g_core_handle, "retro_load_game");
    p_retro_unload_game = (void (*)(void))dlsym(g_core_handle, "retro_unload_game");
    p_retro_run = (void (*)(void))dlsym(g_core_handle, "retro_run");
    p_retro_reset = (void (*)(void))dlsym(g_core_handle, "retro_reset");
    p_retro_serialize_size = (size_t (*)(void))dlsym(g_core_handle, "retro_serialize_size");
    p_retro_serialize = (bool (*)(void *, size_t))dlsym(g_core_handle, "retro_serialize");
    p_retro_unserialize = (bool (*)(const void *, size_t))dlsym(g_core_handle, "retro_unserialize");
    p_retro_get_memory_data = (void *(*)(unsigned))dlsym(g_core_handle, "retro_get_memory_data");
    p_retro_get_memory_size = (size_t (*)(unsigned))dlsym(g_core_handle, "retro_get_memory_size");

    if (!p_retro_init || !p_retro_load_game || !p_retro_run) {
        LOGE("Core no exporta funciones esenciales de Libretro");
        dlclose(g_core_handle);
        g_core_handle = NULL;
        return JNI_FALSE;
    }

    p_retro_set_environment(core_environment_cb);
    p_retro_set_video_refresh(core_video_refresh_cb);
    p_retro_set_audio_sample(core_audio_sample_cb);
    p_retro_set_audio_sample_batch(core_audio_sample_batch_cb);
    p_retro_set_input_poll(core_input_poll_cb);
    p_retro_set_input_state(core_input_state_cb);

    p_retro_init();
    LOGI("retro_init ejecutado exitosamente");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeSetSurface(JNIEnv *env, jobject thiz, jobject surface) {
    if (g_native_window) {
        ANativeWindow_release(g_native_window);
        g_native_window = NULL;
        g_current_buf_width = 0;
        g_current_buf_height = 0;
    }
    if (surface) {
        g_native_window = ANativeWindow_fromSurface(env, surface);
        if (g_native_window && g_av_info.geometry.base_width > 0 && g_av_info.geometry.base_height > 0) {
            ANativeWindow_setBuffersGeometry(g_native_window, g_av_info.geometry.base_width, g_av_info.geometry.base_height, WINDOW_FORMAT_RGBA_8888);
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeLoadGame(JNIEnv *env, jobject thiz, jstring romPath, jstring sramPath) {
    if (!p_retro_load_game) return JNI_FALSE;

    const char *rom_path = env->GetStringUTFChars(romPath, NULL);

    FILE *f = fopen(rom_path, "rb");
    if (!f) {
        LOGE("No se pudo abrir archivo ROM: %s", rom_path);
        env->ReleaseStringUTFChars(romPath, rom_path);
        return JNI_FALSE;
    }

    fseek(f, 0, SEEK_END);
    g_rom_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    struct retro_game_info game_info = {0};
    game_info.path = rom_path;

    // Si la ROM es de cartucho (<= 32 MB), proveer los bytes en memoria RAM
    // para que cores como Genesis Plus GX y Snes9x verifiquen la cabecera
    if (g_rom_size > 0 && g_rom_size <= 32 * 1024 * 1024) {
        if (g_rom_data) free(g_rom_data);
        g_rom_data = (uint8_t *)malloc(g_rom_size);
        if (g_rom_data) {
            fread(g_rom_data, 1, g_rom_size, f);
            game_info.data = g_rom_data;
            game_info.size = g_rom_size;
            LOGI("ROM cargada en memoria: %zu bytes", g_rom_size);
        }
    } else {
        LOGI("Juego de disco masivo (>32MB) referenciado por ruta: %s", rom_path);
    }
    fclose(f);

    bool loaded = p_retro_load_game(&game_info);
    env->ReleaseStringUTFChars(romPath, rom_path);

    if (!loaded) {
        LOGE("Fallo en p_retro_load_game");
        free(g_rom_data);
        g_rom_data = NULL;
        return JNI_FALSE;
    }

    if (p_retro_get_system_av_info) {
        p_retro_get_system_av_info(&g_av_info);
        LOGI("Juego cargado: Resolucion base: %ux%u (max: %ux%u), FPS: %.2f",
             g_av_info.geometry.base_width, g_av_info.geometry.base_height,
             g_av_info.geometry.max_width, g_av_info.geometry.max_height,
             g_av_info.timing.fps);

        if (g_native_window) {
            ANativeWindow_setBuffersGeometry(g_native_window, g_av_info.geometry.base_width, g_av_info.geometry.base_height, WINDOW_FORMAT_RGBA_8888);
        }
    }

    // Load SRAM if available
    if (sramPath && p_retro_get_memory_data && p_retro_get_memory_size) {
        const char *sram_p = env->GetStringUTFChars(sramPath, NULL);
        FILE *fsram = fopen(sram_p, "rb");
        if (fsram) {
            void *sram_mem = p_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
            size_t sram_sz = p_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
            if (sram_mem && sram_sz > 0) {
                fread(sram_mem, 1, sram_sz, fsram);
                LOGI("SRAM cargada: %zu bytes", sram_sz);
            }
            fclose(fsram);
        }
        env->ReleaseStringUTFChars(sramPath, sram_p);
    }

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeRunFrame(JNIEnv *env, jobject thiz) {
    if (p_retro_run) {
        p_retro_run();
    }
}

JNIEXPORT void JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeSendInput(JNIEnv *env, jobject thiz, jint port, jint device, jint index, jint id, jboolean pressed) {
    if (port < 4 && id < 16) {
        g_button_states[port][id] = pressed;
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeGetSramData(JNIEnv *env, jobject thiz) {
    if (!p_retro_get_memory_data || !p_retro_get_memory_size) return NULL;
    void *data = p_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = p_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return NULL;

    jbyteArray arr = env->NewByteArray(size);
    if (arr) {
        env->SetByteArrayRegion(arr, 0, size, (const jbyte *)data);
    }
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeSetSramData(JNIEnv *env, jobject thiz, jbyteArray data) {
    if (!p_retro_get_memory_data || !p_retro_get_memory_size || !data) return JNI_FALSE;
    void *mem = p_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = p_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!mem || size == 0) return JNI_FALSE;

    jsize len = env->GetArrayLength(data);
    size_t to_copy = (len < (jsize)size) ? len : size;
    env->GetByteArrayRegion(data, 0, to_copy, (jbyte *)mem);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeSaveState(JNIEnv *env, jobject thiz, jstring statePath) {
    if (!p_retro_serialize || !p_retro_serialize_size) return JNI_FALSE;
    size_t size = p_retro_serialize_size();
    if (size == 0) return JNI_FALSE;

    void *buf = malloc(size);
    if (!buf) return JNI_FALSE;

    bool ok = p_retro_serialize(buf, size);
    if (ok) {
        const char *path = env->GetStringUTFChars(statePath, NULL);
        FILE *f = fopen(path, "wb");
        if (f) {
            fwrite(buf, 1, size, f);
            fclose(f);
        } else {
            ok = false;
        }
        env->ReleaseStringUTFChars(statePath, path);
    }
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeLoadState(JNIEnv *env, jobject thiz, jstring statePath) {
    if (!p_retro_unserialize || !p_retro_serialize_size) return JNI_FALSE;
    size_t size = p_retro_serialize_size();
    if (size == 0) return JNI_FALSE;

    const char *path = env->GetStringUTFChars(statePath, NULL);
    FILE *f = fopen(path, "rb");
    if (!f) {
        env->ReleaseStringUTFChars(statePath, path);
        return JNI_FALSE;
    }

    void *buf = malloc(size);
    if (!buf) {
        fclose(f);
        env->ReleaseStringUTFChars(statePath, path);
        return JNI_FALSE;
    }

    size_t read_bytes = fread(buf, 1, size, f);
    fclose(f);
    env->ReleaseStringUTFChars(statePath, path);

    bool ok = (read_bytes == size) && p_retro_unserialize(buf, size);
    free(buf);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeUnloadGame(JNIEnv *env, jobject thiz) {
    if (p_retro_unload_game) {
        p_retro_unload_game();
    }
    if (g_rom_data) {
        free(g_rom_data);
        g_rom_data = NULL;
        g_rom_size = 0;
    }
}

JNIEXPORT void JNICALL
Java_com_retro_tvbox_core_LibretroBridge_nativeDeinitCore(JNIEnv *env, jobject thiz) {
    if (p_retro_deinit) {
        p_retro_deinit();
    }
    if (g_core_handle) {
        dlclose(g_core_handle);
        g_core_handle = NULL;
    }
    if (g_bridge_obj) {
        env->DeleteGlobalRef(g_bridge_obj);
        g_bridge_obj = NULL;
    }
}

} // extern "C"
