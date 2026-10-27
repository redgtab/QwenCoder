# BiliLite TV

哔哩哔哩第三方 **Android TV 专用**客户端（基于公开 API，参考 bbll / blbl / NewBV 等项目）。
从 v2.0.1 起**不再提供手机版**，仅发布 TV 版 APK。

## 基本要求
- Android **4.4.3+ (API 19)** 的 Android TV / 智能电视盒子
- 遥控器 D-Pad 操作（返回键：非首页时自动回首页）

## 功能
- 热门 / 排行 / 搜索（WBI 签名）
- 扫码登录（TV 二维码）
- 视频详情、分P、内置播放 + 外部播放器
- 弹幕渲染（GZIP 解析、密度设置）
- 画质切换（480P/720P/1080P/高码率/4K）
- 评论、动态（自己/他人 UID）、关注/粉丝、收藏夹、历史记录
- 点赞 / 投币 / 三连 / 收藏 / 发弹幕
- 设置页（默认画质、弹幕开关与密度、缓存清理等）
- **检查更新**：读取仓库根目录 `update.txt`（每次发版随 APK 一并提交），失败回退 GitHub Releases API
- **崩溃收集**：未处理异常写入本地日志，可在 设置→崩溃日志 查看、复制、一键跳转 GitHub Issue 提交反馈

## 版本规则
版本号自 v2.0.1 起按补丁顺序递增：v2.0.1 → v2.0.2 → v2.0.3 …（不升大版本）。

## update.txt 格式
```
version=2.0.1
notes=更新点1|更新点2|更新点3
```

## 下载
见 [Releases](../../releases)，文件名形如 `BiliLite-vX.Y.Z-TV-debug.apk`。
安装：U盘/ADB `adb install BiliLite-vX.Y.Z-TV-debug.apk`。

## 构建
```
gradle assembleDebug   # minSdk 19, targetSdk 29, JDK 17
```

## 免责声明
仅供学习交流，请勿用于商业用途。所有数据来自哔哩哔哩公开接口。
