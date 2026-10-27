package com.bililite.app.api;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 视频条目统一模型（热门 / 排行 / 搜索 / 收藏 / 历史 通用）
 */
public class VideoItem {
    public long aid;        // 旧式 avid
    public String bvid;     // 新式 bvid
    public String title;
    public String cover;    // 封面 url（可能无协议头）
    public String uploader; // UP 主
    public long mid;        // UP 主 mid
    public String duration; // 格式化时长 mm:ss / hh:mm:ss
    public long durationSec;
    public String plays;    // 播放量文本
    public String danmaku;  // 弹幕数文本
    public String dateText; // 发布日期 / 时间文本
    public String intro;    // 简介

    public String url() {
        if (cover == null) return null;
        if (cover.startsWith("//")) return "https:" + cover;
        return cover;
    }

    public String playUrl() {
        if (bvid != null && bvid.startsWith("BV")) return "https://www.bilibili.com/video/" + bvid;
        return "https://www.bilibili.com/video/av" + aid;
    }

    public static String fmtCount(long n) {
        if (n >= 10000) return String.format(java.util.Locale.US, "%.1f万", n / 10000.0);
        return String.valueOf(n);
    }

    public static String fmtDuration(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    /** 从 app 端 popular/ranking/feed 风格的 json 构造（兼容多种字段名） */
    public static VideoItem fromAppJson(JSONObject o) {
        VideoItem v = new VideoItem();
        v.bvid = o.optString("bvid", o.optString("bv_id", null));
        v.aid = o.optLong("aid", o.optLong("id", 0));
        v.title = o.optString("title", "").replace("<em class=\"keyword\">", "")
                .replace("</em>", "");
        v.cover = o.optString("pic", o.optString("cover", ""));
        JSONObject owner = o.optJSONObject("owner");
        if (owner != null) {
            v.uploader = owner.optString("name", "");
            v.mid = owner.optLong("mid", 0);
        } else {
            v.uploader = o.optString("author", o.optString("up", ""));
            v.mid = o.optLong("mid", 0);
        }
        v.durationSec = o.optLong("duration", o.optLong("length", 0));
        Object d = o.opt("duration");
        if (d instanceof String && ((String) d).contains(":")) {
            v.duration = (String) d;
        } else {
            v.duration = fmtDuration(v.durationSec);
        }
        v.plays = o.optString("play", "");
        if (v.plays.isEmpty()) v.plays = fmtCount(o.optLong("stat_play", 0));
        v.danmaku = o.optString("video_review", "");
        if (v.danmaku.isEmpty()) v.danmaku = fmtCount(o.optLong("stat_danmaku", 0));
        v.dateText = o.optString("pubdate_str", o.optString("created", ""));
        if (v.dateText.isEmpty()) {
            long ts = o.optLong("pubdate", o.optLong("senddate", 0));
            if (ts > 0) {
                v.dateText = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(new java.util.Date(ts * 1000L));
            }
        }
        v.intro = o.optString("desc", o.optString("intro", ""));
        return v;
    }

    public static java.util.List<VideoItem> listFrom(JSONArray arr) {
        java.util.List<VideoItem> out = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(fromAppJson(o));
        }
        return out;
    }
}
