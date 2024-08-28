package cpcextension

import (
        "android/soong/android"
        "android/soong/cc"
)

func init() {
    android.RegisterModuleType("cpc_library_shared", cpcextensionDefaultsFactory)
}

func cpcextensionDefaultsFactory() (android.Module) {
    module := cc.LibrarySharedFactory()
    android.AddLoadHook(module, cpcextensionHook)
    return module
}

func cpcextensionHook(ctx android.LoadHookContext) {
    //AConfig() function is at build/soong/android/config.go

    Version := ctx.AConfig().PlatformVersionName()

    type props struct {
        Srcs []string
        Static_libs []string
        Shared_libs []string
        Cflags []string
    }

    p := &props{}

    if (Version == "12") {
        p.Cflags = append(p.Cflags, "-DNO_CPC_BINDER")
        p.Cflags = append(p.Cflags, "-Wno-unused-parameter")
        p.Shared_libs = append(p.Shared_libs, "libandroid_runtime")
        p.Shared_libs = append(p.Shared_libs, "libbase")
        p.Shared_libs = append(p.Shared_libs, "liblog")
        p.Shared_libs = append(p.Shared_libs, "libcutils")
        p.Shared_libs = append(p.Shared_libs, "libutils")
        p.Shared_libs = append(p.Shared_libs, "libbinder")
        p.Shared_libs = append(p.Shared_libs, "libandroid")
        p.Shared_libs = append(p.Shared_libs, "libandroid_runtime_lazy")
        p.Shared_libs = append(p.Shared_libs, "libnativehelper")
    } else {
        p.Srcs = append(p.Srcs, ":cpc_binder_jni")
        p.Static_libs = append(p.Static_libs, "libcpcbinder")
    }

    ctx.AppendProperties(p)
}
