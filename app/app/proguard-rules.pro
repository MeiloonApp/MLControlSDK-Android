# MLControlCoreAOS App ProGuard Rules

# --- 基本 Kotlin 規則 ---
-dontwarn kotlin.**
-keep class kotlin.** { *; }

# --- EventBus ---
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# --- Retrofit / Gson / OkHttp ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public static **[] values();
  public static ** valueOf(java.lang.String);
}

# --- RxAndroidBle / RxJava ---
-keep class com.polidea.rxandroidble3.** { *; }
-keep class io.reactivex.rxjava3.** { *; }

# --- PermissionX ---
-keep class com.guolindev.permissionx.** { *; }

# --- AnimatedBottomBar ---
-keep class nl.joery.animatedbottombar.** { *; }

# --- App 自身的 Data 與 ViewBinding ---
-keep class com.meiloon.mlcontrolcore_aos.data.** { *; }
-keep class com.meiloon.mlcontrolcore_aos.databinding.** { *; }

# --- 確保 ControlCoreModule 的 AAR 內容不被過度混淆 (保險起見) ---
-keep public class com.meiloon.controlcore.** {
    public protected *;
}
-keep class com.meiloon.controlcore.main.api.** { *; }
-keep class com.meiloon.controlcore.global.database.entity.** { *; }
-keep class com.meiloon.controlcore.main.container.event.** { *; }
-keep class com.meiloon.controlcore.main.widget.ble.event.** { *; }
