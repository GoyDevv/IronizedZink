/*
 * Ironized Zink — GL front-end wrapper.
 *
 * The bundled Zink GL entry library (libglxshim.so) exposes exactly one bootstrap
 * symbol, glXGetProcAddress; LWJGL obtains every GL entry point through it. This
 * wrapper is loaded in libglxshim's place (the plugin's glName), forwards
 * glXGetProcAddress to the real libglxshim, and returns a custom glGetString that
 * reports GL_RENDERER as "IronizedZink | OpenGL <version>" (so Minecraft's F3 shows
 * the renderer name, like MobileGlues). Everything else is passed through unchanged,
 * so if resolution ever fails the renderer still works — only the F3 string is affected.
 *
 * Licensed under GPL-3.0 (see LICENSE).
 */
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <android/log.h>

#define TAG "IronizedZink"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef unsigned int  GLenum;
typedef unsigned char GLubyte;
typedef void (*GLproc)(void);

#define GL_VENDOR   0x1F00
#define GL_RENDERER 0x1F01
#define GL_VERSION  0x1F02

static GLproc (*real_gpa)(const GLubyte *) = NULL;      /* real glXGetProcAddress */
static const GLubyte *(*real_getstring)(GLenum) = NULL; /* real glGetString */

static void resolve_backend(void) {
    if (real_gpa) return;
    /* libglxshim.so lives in our nativeLibraryDir, which is on LD_LIBRARY_PATH. */
    void *h = dlopen("libglxshim.so", RTLD_NOW | RTLD_GLOBAL);
    if (h) real_gpa = (GLproc (*)(const GLubyte *)) dlsym(h, "glXGetProcAddress");
    if (!real_gpa) real_gpa = (GLproc (*)(const GLubyte *)) dlsym(RTLD_NEXT, "glXGetProcAddress");
    if (real_gpa) LOGI("GL wrapper bound to libglxshim glXGetProcAddress");
}

/* Our glGetString: only GL_RENDERER is customised; everything else passes through. */
static const GLubyte *iz_glGetString(GLenum name) {
    if (!real_getstring) {
        resolve_backend();
        if (real_gpa) real_getstring = (const GLubyte *(*)(GLenum)) real_gpa((const GLubyte *) "glGetString");
    }
    if (name == GL_RENDERER) {
        static char buf[192];
        char ver[48] = "";
        if (real_getstring) {
            const GLubyte *v = real_getstring(GL_VERSION);   /* e.g. "4.6 (Compatibility...) Mesa 23.0.4" */
            if (v) {
                int i = 0;
                while (v[i] && v[i] != ' ' && i < (int) sizeof(ver) - 1) { ver[i] = (char) v[i]; i++; }
                ver[i] = '\0';
            }
        }
        if (ver[0]) snprintf(buf, sizeof buf, "IronizedZink | OpenGL %s", ver);
        else        snprintf(buf, sizeof buf, "IronizedZink | OpenGL");
        return (const GLubyte *) buf;
    }
    return real_getstring ? real_getstring(name) : (const GLubyte *) "";
}

/* Exported bootstrap symbol LWJGL uses to fetch every GL entry point. */
__attribute__((visibility("default")))
GLproc glXGetProcAddress(const GLubyte *procName) {
    resolve_backend();
    if (procName && strcmp((const char *) procName, "glGetString") == 0) {
        return (GLproc) iz_glGetString;
    }
    return real_gpa ? real_gpa(procName) : NULL;
}

__attribute__((visibility("default")))
GLproc glXGetProcAddressARB(const GLubyte *procName) {
    return glXGetProcAddress(procName);
}

/* Bind to libglxshim as soon as this wrapper is loaded, matching the original
 * load timing (rather than lazily on first use). */
__attribute__((constructor))
static void iz_gl_init(void) {
    resolve_backend();
    LOGI("Ironized Zink GL wrapper loaded (by GoyDevv)");
}
