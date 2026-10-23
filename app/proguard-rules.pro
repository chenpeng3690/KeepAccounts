# 移除日志调用
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Room 实体与 Dao 由注解处理器生成，无需额外 keep
-keep class androidx.room.RoomDatabase { *; }

# Compose 运行时
-keep class androidx.compose.runtime.** { *; }
# Compose UI 含大量 Kotlin 默认方法接口，避免 R8 优化后出现默认方法冲突
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# 通知监听服务需保留类名供系统绑定
-keep class com.local.notebook.service.NotiListenerService { *; }
