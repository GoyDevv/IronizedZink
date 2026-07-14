# Credits & Attribution

**Ironized Zink** is a project by **GoyDevv**. It stands entirely on the shoulders of the
open-source graphics community: it is a **repackaging and configuration layer** around an
existing OpenGL-over-Vulkan driver and does not reimplement any of the rendering
technology itself. Full credit for the renderer goes to the **Zink / Mesa** authors, and
to the **Fold Craft Launcher** team for the modified Android Zink build and the
renderer-plugin interface.

## The rendering engine — Mesa / Zink / Kopper

The renderer is the **Zink** Gallium driver from the **Mesa 3-D Graphics Library**
(Mesa `23.0.4`), together with the **Kopper** window-system integration layer that lets
Zink present through Vulkan swapchains.

- **Project:** Mesa 3D — https://mesa3d.org / https://gitlab.freedesktop.org/mesa/mesa
- **Zink:** OpenGL implemented on top of Vulkan (`src/gallium/drivers/zink`)
- **Kopper:** Vulkan WSI/swapchain integration for Zink
- **Copyright:** © 1999–2007 Brian Paul and the Mesa contributors, and the respective
  authors of each component.
- **License:** MIT for the core Mesa and Gallium code (which is what this plugin ships);
  the GLX client code uses the SGI Free Software License B and the GL headers use the
  Khronos license. The full text is bundled in the app at
  `app/src/main/assets/licenses/mesa-licenses.rst`.

> Per Mesa's request: this software must not be referred to as "MesaGL" — it is *Mesa*,
> or *The Mesa 3-D Graphics Library*.

The prebuilt Android binaries of the Zink/Kopper stack
(`libEGL_mesa.so`, `libglxshim.so`, `libglapi.so`, `libzink_dri.so`, `libcutils.so`)
originate from the AngelAuraMC "mesa_zink_kopper" build of Mesa for Android.

## The launcher plugin interface

The renderer-plugin contract this APK targets (the `fclPlugin` / `zalithRendererPlugin`
manifest metadata, and the `renderer` / `pojavEnv` / `boatEnv` keys) was defined by the
**Fold Craft Launcher (FCL)** project and adopted by the **ZalithLauncher 2** project.

- **Fold Craft Launcher:** https://github.com/FCL-Team/FoldCraftLauncher — GPL-3.0
- **ZalithLauncher 2:** https://github.com/ZalithLauncher/ZalithLauncher2 — GPL-3.0
- Both build on **PojavLauncher** as their launching engine.

Because Ironized Zink derives its plugin interface and environment configuration from
these GPL-3.0 projects, Ironized Zink's own integration code is likewise released under
the **GNU General Public License v3.0** (see `LICENSE`).

## Summary of licenses

| Component                                   | License        |
|---------------------------------------------|----------------|
| Ironized Zink integration code (this repo)  | GPL-3.0        |
| Bundled Mesa core + Gallium (Zink/Kopper)   | MIT            |
| Mesa GLX client code                        | SGI B          |
| GL / GLX headers                            | Khronos        |

Ironized Zink is an independent project and is **not** affiliated with or endorsed by
the Mesa project, the Fold Craft Launcher team, the ZalithLauncher team, or Mojang/Microsoft.
Minecraft is a trademark of Mojang Synergies AB.
