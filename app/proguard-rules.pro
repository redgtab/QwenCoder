# 纯 Java + OkHttp 方案，不引入 IJK/ExoPlayer 等原生库，保证 API19 兼容与可编译性；
# 视频播放通过 Intent 交给系统播放器处理
-keepattributes Signature
-keep class com.bililite.app.** { *; }
