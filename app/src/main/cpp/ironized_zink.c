/*
 * Ironized Zink — runtime config shim.
 *
 * The launcher dlopen()s this library (declared via `DLOPEN=libironized_zink.so`
 * in the plugin's pojavEnv) into the game process during launch setup, BEFORE the
 * JVM starts Minecraft and long before Mesa/Zink reads its environment at GL-context
 * creation. Our constructor therefore has a safe window to apply the user's saved
 * preset by calling setenv() for a whitelisted set of Mesa/Zink variables.
 *
 * Design goals:
 *   - Never crash the game: everything is bounded and failure is a silent no-op.
 *   - Deterministic: if a config file exists we first clear the optional knobs, then
 *     apply exactly what the file says (so a toggle turned OFF really is off).
 *   - Safe: only a fixed allow-list of GL/Mesa/Zink keys may be set.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "IronizedZink"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

/* Candidate config paths, in priority order. The settings screen writes the first. */
static const char *const CONFIG_PATHS[] = {
    "/sdcard/IronizedZink/ironized.env",
    "/storage/emulated/0/IronizedZink/ironized.env",
    "/sdcard/Download/IronizedZink/ironized.env",
    "/storage/emulated/0/Download/IronizedZink/ironized.env",
    NULL,
};

/* Optional knobs cleared before applying the file so "off" is authoritative. */
static const char *const OPTIONAL_KEYS[] = {
    "mesa_glthread", "MESA_NO_ERROR", "GALLIUM_HUD", "LIBGL_ALWAYS_SOFTWARE",
    "force_glsl_extensions_warn", "allow_higher_compat_version",
    "allow_glsl_extension_directive_midshader", "MESA_EXTENSION_MAX_YEAR",
    "MESA_DISK_CACHE_SINGLE_FILE", "MESA_SHADER_CACHE_DISABLE", "vblank_mode",
    "LIBGL_SHOW_FPS", "POJAV_BIG_CORE_AFFINITY", "FORCE_VSYNC",
    NULL,
};

/* Only keys matching one of these prefixes may be set (defence-in-depth). */
static const char *const ALLOWED_PREFIXES[] = {
    "MESA_", "mesa_", "LIBGL_", "GALLIUM_", "ZINK_", "POJAV_", "FORCE_VSYNC",
    "force_glsl_", "allow_", "vblank_mode",
    NULL,
};

static int key_allowed(const char *key) {
    for (int i = 0; ALLOWED_PREFIXES[i]; i++) {
        size_t n = strlen(ALLOWED_PREFIXES[i]);
        if (strncmp(key, ALLOWED_PREFIXES[i], n) == 0) return 1;
    }
    return 0;
}

static char *trim(char *s) {
    while (*s == ' ' || *s == '\t') s++;
    size_t len = strlen(s);
    while (len > 0 && (s[len - 1] == ' ' || s[len - 1] == '\t' ||
                       s[len - 1] == '\r' || s[len - 1] == '\n')) {
        s[--len] = '\0';
    }
    return s;
}

static void apply_config(const char *path) {
    FILE *f = fopen(path, "r");
    if (!f) return;

    LOGI("applying renderer config: %s", path);
    /* Clear optional knobs so the file is the single source of truth. */
    for (int i = 0; OPTIONAL_KEYS[i]; i++) unsetenv(OPTIONAL_KEYS[i]);

    char line[1024];
    int applied = 0;
    while (fgets(line, sizeof(line), f)) {
        char *eq = strchr(line, '=');
        if (!eq) continue;
        *eq = '\0';
        char *key = trim(line);
        char *val = trim(eq + 1);
        if (key[0] == '\0' || key[0] == '#') continue;
        if (!key_allowed(key)) {
            LOGW("ignoring non-whitelisted key: %s", key);
            continue;
        }
        setenv(key, val, 1);
        applied++;
    }
    fclose(f);
    LOGI("applied %d environment override(s)", applied);
}

__attribute__((constructor))
static void ironized_zink_init(void) {
    LOGI("Ironized Zink shim loaded (by GoyDevv)");
    for (int i = 0; CONFIG_PATHS[i]; i++) {
        FILE *probe = fopen(CONFIG_PATHS[i], "r");
        if (probe) {
            fclose(probe);
            apply_config(CONFIG_PATHS[i]);
            return;
        }
    }
    LOGI("no config file found — using launcher/manifest baseline");
}
