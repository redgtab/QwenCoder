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

    /** 从 web 端 JSON 构造（popular/ranking/search/history/fav 各种字段名兼容） */
    public static VideoItem fromWebJson(JSONObject o) {
        VideoItem v = new VideoItem();
        v.bvid = firstNonEmpty(o.optString("bvid", ""), o.optString("bv_id", ""), o.optString("short_link_v2", ""));
        if (v.bvid != null && v.bvid.contains("BV")) {
            int i = v.bvid.indexOf("BV");
            String t = v.bvid.substring(i);
            int e = t.indexOf("?");
            v.bvid = e > 0 ? t.substring(0, e) : t;
        } else v.bvid = null;
        v.aid = o.optLong("aid", o.optLong("id", 0));
        v.title = cleanEm(o.optString("title", ""));
        v.cover = firstNonEmpty(o.optString("pic", ""), o.optString("cover", ""), o.optString("thumbnail_index", ""));
        JSONObject owner = o.optJSONObject("owner");
        if (owner != null) { v.uploader = owner.optString("name", ""); v.mid = owner.optLong("mid", 0); }
        else {
            v.uploader = firstNonEmpty(o.optString("author", ""), o.optString("up_name", ""),
                    o.optString("uname", ""), o.optString("name", ""));
            v.mid = o.optLong("mid", o.optLong("uid", 0));
        }
        // history: {title, covers[], author_name, business, uri ...}
        JSONArray covers = o.optJSONArray("covers");
        if ((v.cover == null || v.cover.isEmpty()) && covers != null && covers.length() > 0)
            v.cover = covers.optString(0);
        if (v.uploader.isEmpty()) v.uploader = o.optString("author_name", "");
        if (v.aid == 0) v.aid = o.optLong("show_title", 0) == 0 ? o.optLong("business_id", 0) : 0;

        JSONObject stat = o.optJSONObject("stat");
        long play = stat != null ? stat.optLong("view", 0) : o.optLong("play", o.optLong("player_data_play", 0));
        long dm = stat != null ? stat.optLong("danmaku", 0) : o.optLong("video_review", o.optLong("player_data_count", 0));
        v.plays = fmtCount(play);
        v.danmaku = fmtCount(dm);

        long dur = o.optLong("duration", o.optLong("length", 0));
        if (dur == 0 && stat != null) dur = stat.optLong("duration", 0);
        Object ds = o.opt("duration");
        if (ds instanceof String && ((String) ds).contains(":")) v.duration = (String) ds;
        else v.duration = fmtDuration(dur);
        v.durationSec = dur;

        long pub = o.optLong("pubdate", o.optLong("created", o.optLong("timestamp", 0)));
        if (pub > 0) v.dateText = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(pub * 1000L));
        else v.dateText = o.optString("pubdate_str", o.optString("sendtime_desc", ""));
        v.intro = cleanEm(firstNonEmpty(o.optString("desc", ""), o.optString("intro", ""), o.optString("rcmd_reason", "")));
        return v;
    }

    private static String firstNonEmpty(String... arr) {
        for (String s : arr) if (s != null && !s.isEmpty()) return s;
        return "";
    }

    private static String cleanEm(String s) {
        return s.replace("<em class=\"keyword\">", "").replace("</em>", "");
    }

    /** 自动识别 web/app 字段风格：先 app 再补空字段用 web */
    public static VideoItem fromAny(JSONObject o) {
        VideoItem w = fromWebJson(o);
        VideoItem a = fromAppJson(o);
        if (a.title == null || a.title.isEmpty()) a.title = w.title;
        if (a.cover == null || a.cover.isEmpty()) a.cover = w.cover;
        if (a.bvid == null) a.bvid = w.bvid;
        if (a.aid == 0) a.aid = w.aid;
        if (a.uploader == null || a.uploader.isEmpty()) a.uploader = w.uploader;
        if (a.mid == 0) a.mid = w.mid;
        if (a.plays == null || a.plays.isEmpty() || a.plays.equals("0")) a.plays = w.plays;
        if (a.danmaku == null || a.danmaku.isEmpty() || a.danmaku.equals("0")) a.danmaku = w.danmaku;
        if (a.dateText == null || a.dateText.isEmpty()) a.dateText = w.dateText;
        if (a.duration == null || a.duration.isEmpty()) a.duration = w.duration;
        return a;
    }

    public static java.util.List<VideoItem> listFromWeb(JSONArray arr) {
        java.util.List<VideoItem> out = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(fromAny(o));
        }
        return out;
    }
}
