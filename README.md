# BiliLite

一个基于哔哩哔哩公开 API 的第三方 Android 客户端，支持 **手机 (Android 4.4.3+ / API 19)** 与 **Android TV**（遥控器焦点导航）。界面简洁，设计参考 [bbll](https://github.com/luoxie00/bbll)、[bv](https://github.com/dooy/bv)、[newbv](https://github.com/newbv)、[blbl](https://github.com/Nicklaus233/blbl) 等项目。

## 功能
- 🏠 热门 / 🔥 排行榜 / 🔍 搜索 / ⭐ 我的收藏 / 🕘 观看历史
- 👤 UP 主空间投稿列表、视频详情与分 P 列表
- 📱 扫码登录（TV OAuth 接口，zxing 本地生成二维码 + 轮询登录状态）
- 📺 TV 模式：Leanback Launcher 入口 + Banner，D-Pad 焦点高亮，调用系统播放器播放
- 轻量：minSdk 19，无重型依赖（OkHttp 3.12 + zxing core）

## 构建
```bash
# 需要 JDK 17、Android SDK (platform 34, build-tools 34.0.0)
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 安装
```bash
adb install app-debug.apk          # 手机或电视均可
```

## 技术说明
- `app/src/main/java/com/bililite/app/api/BiliApi.java` — API 封装（md5 签名、Cookie 持久化 SESSDATA）
- `ui/` — MainActivity(五 Tab)、SearchActivity、UserSpaceActivity、PlayActivity、LoginActivity、DetailsActivity(TV 转发)
- 主题：`Theme.BiliLite`(手机浅色) / `Theme.BiliLite.TV`(电视深色)

## 免责声明
本项目仅供学习交流，请勿用于商业用途。所有数据接口归属哔哩哔哩，使用造成的任何后果自负。

## v2.0 更新内容
- 修复登录（TV 扫码 + bili_jct 提取）与视频列表获取（web 接口 + app 端兜底、WBI 签名）
- 新增：评论区、动态（自己的关注流 & 输入 UID 查看他人动态）、关注/粉丝列表、收藏夹二级页
- 新增：内置播放（VideoView + 弹幕 DanmakuView 滚动渲染）、画质切换（720P/1080P/高码率/4K）
- 新增：设置页（默认画质、弹幕开关、弹幕密度、自动播放、检查更新 GitHub Release、清缓存、关于）
- 性能优化：图片懒加载缓存、分页预加载、IO 线程池、GZIP 弹幕解析
