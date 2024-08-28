package cpctest

import (
        "android/soong/android"
        "android/soong/java"
)

func init() {
    android.RegisterModuleType("cpctest_defaults", cpcTestDefaultsFactory)
}

func cpcTestDefaultsFactory() (android.Module) {
    module := java.DefaultsFactory()
    android.AddLoadHook(module, cpcTestHook)
    return module
}

func cpcTestHook(ctx android.LoadHookContext) {
    //AConfig() function is at build/soong/android/config.go

    Version := ctx.AConfig().PlatformVersionName()

    type props struct {
        Enabled *bool
    }

    p := &props{}

    var enabled bool = true

    if (Version == "12") {
        enabled = false
    }

    p.Enabled = &enabled

    ctx.AppendProperties(p)
}
