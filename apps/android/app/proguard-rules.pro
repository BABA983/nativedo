# Rewrite bootstrap entry points.
-keep class io.github.baba983.nativedo.NativeDoApplication { *; }
-keep class io.github.baba983.nativedo.MainActivity { *; }
-keep class io.github.baba983.nativedo.NdApp { *; }
-keep class io.github.baba983.nativedo.NdRuntime { *; }

# Preserve rewrite fragment constructors for ActionBarLayout state restore.
-keep class io.github.baba983.nativedo.ui.actionbar.** extends io.github.baba983.nativedo.ui.actionbar.BaseFragment {
    public <init>(...);
}

# Preserve kotlinx.serialization companions / serializers for new discourse models.
-keepclassmembers class io.github.baba983.nativedo.discourse.model.** {
    *** Companion;
}
-keep class io.github.baba983.nativedo.discourse.model.**$$serializer { *; }
-keepclassmembers class io.github.baba983.nativedo.discourse.model.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
