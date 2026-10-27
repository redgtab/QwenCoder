package com.bililite.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 哔哩哔哩公开 API 封装（参考 BiliBiliTik / bbll / NewBV 等项目使用的接口）
 * - app 端公开接口：热门、排行、搜索、收藏、历史、用户信息、扫码登录
 * - 使用 OkHttp CookieJar 持久化登录态（SESSDATA / bili_jct）
 */
public class BiliApi {

    public interface CB {
        void onResult(JSONObject data, String err);
    }

    static final String UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";
    private static final String APP_APPKEY = "1d8b6e3d45234290a1f6b4af90726f0a"; // bilili app 公开 appkey
    private static final String APP_SECRET = "5b2ac65ac03f6ca36030e6ec692c0bc5";
    private static final String WEB_APPKEY = "7d07345c677803689e9705400c02c609"; // 网页端 tv 登录 appkey
    private static final String WEB_SECRET = "c2ed53a74eeefe3cf9921a01f1d1c947";

    private static BiliApi sInstance;
    private final OkHttpClient client;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);

    public static synchronized BiliApi get(Context ctx) {
        if (sInstance == null) sInstance = new BiliApi(ctx.getApplicationContext());
        return sInstance;
    }

    private BiliApi(Context ctx) {
        prefs = ctx.getSharedPreferences("bili_cookie", Context.MODE_PRIVATE);
        client = new OkHttpClient.Builder()
                .cookieJar(new PersistCookieJar(prefs))
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* 登录态                                                              */
    /* ------------------------------------------------------------------ */

    public boolean isLogin() {
        return !prefs.getString("SESSDATA", "").isEmpty();
    }

    public String uname() { return prefs.getString("uname", ""); }
    public long uid() { return prefs.getLong("uid", 0); }
    public String face() { return prefs.getString("face", ""); }

    public void logout() {
        prefs.edit().clear().apply();
    }

    /** 获取当前登录用户信息（web nav 接口，依赖 SESSDATA cookie）-> {name, mid, face} */
    public void myInfo(final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson("https://api.bilibili.com/x/web-interface/nav");
                JSONObject d = root.getJSONObject("data");
                if (!d.optBoolean("isLogin")) { post(cb, null, "未登录或 Cookie 已失效"); return; }
                prefs.edit()
                        .putString("uname", d.getString("uname"))
                        .putLong("uid", d.getLong("mid"))
                        .putString("face", d.optString("face"))
                        .apply();
                JSONObject out = new JSONObject();
                out.put("name", d.getString("uname"));
                out.put("mid", d.getLong("mid"));
                out.put("face", d.optString("face"));
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    public String jct() { return prefs.getString("bili_jct", ""); }

    /* Settings 页使用的通用偏好封装 */
    public int prefsInt(String key, int def) { return prefs.getInt(key, def); }
    public void setPrefsInt(String key, int v) { prefs.edit().putInt(key, v).apply(); }
    public boolean prefsBool(String key, boolean def) { return prefs.getBoolean(key, def); }
    public void setPrefsBool(String key, boolean v) { prefs.edit().putBoolean(key, v).apply(); }
    public SharedPreferences prefs() { return prefs; }

    private JSONObject postJson(String url, String form) throws Exception {
        Request req = new okhttp3.Request.Builder().url(url)
                .header("User-Agent", UA)
                .post(okhttp3.RequestBody.create(Http.FORM, form)).build();
        okhttp3.Response r = client.newCall(req).execute();
        String body = r.body() != null ? r.body().string() : "";
        r.close();
        JSONObject root = new JSONObject(body);
        int code = root.optInt("code");
        if (code != 0) throw new Exception(root.optString("message", "code=" + code));
        return root;
    }

    /** 点赞（web，需登录） */
    public void like(final String bvid, final CB cb) {
        io.execute(() -> {
            try {
                postJson("https://api.bilibili.com/x/web/archive/like",
                        "bvid=" + Http.enc(bvid) + "&csrf=" + Http.enc(jct()));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 投币 x1（selection_key 简化省略） */
    public void coin(final String bvid, final CB cb) {
        io.execute(() -> {
            try {
                postJson("https://api.bilibili.com/x/web/coin/add",
                        "bvid=" + Http.enc(bvid) + "&multiply=1&csrf=" + Http.enc(jct()));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 一键三连 */
    public void triple(final String bvid, final CB cb) {
        io.execute(() -> {
            try {
                postJson("https://api.bilibili.com/x/web/archive/onekey",
                        "bvid=" + Http.enc(bvid) + "&csrf=" + Http.enc(jct()));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 收藏到默认收藏夹 */
    public void favDefault(long rid, final CB cb) {
        io.execute(() -> {
            try {
                long fid = prefs.getLong("fav_fid", 0);
                if (fid == 0) { // 先取"默认收藏夹"
                    JSONObject root = Http.getJson("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + uid());
                    JSONArray list = root.getJSONObject("data").optJSONArray("list");
                    for (int i = 0; list != null && i < list.length(); i++) {
                        JSONObject f = list.getJSONObject(i);
                        if (f.getInt("type") == 0) { fid = f.getLong("id"); break; }
                    }
                    if (fid == 0 && list != null && list.length() > 0) fid = list.getJSONObject(0).getLong("id");
                    prefs.edit().putLong("fav_fid", fid).apply();
                }
                postJson("https://api.bilibili.com/x/v3/fav/resource/add",
                        "rid=" + rid + "&type=2&add_media_ids=" + rid + "&fid=" + fid
                                + "&csrf=" + Http.enc(jct()));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 关注/取消关注 UP 主（tag_id=0 默认分组） */
    public void follow(long mid, boolean doFollow, final CB cb) {
        io.execute(() -> {
            try {
                if (doFollow)
                    postJson("https://api.bilibili.com/x/relation/tags/addUsers",
                            "fids=" + mid + "&tagid=0&csrf=" + Http.enc(jct()));
                else
                    postJson("https://api.bilibili.com/x/relation/disfollow",
                            "fid=" + mid + "&csrf=" + Http.enc(jct()));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 发送弹幕（需要登录） */
    public void sendDanmaku(long aid, String text, final CB cb) {
        io.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                postJson("https://api.bilibili.com/x/v2/dm/web/switch", "");
                postJson("https://api.bilibili.com/x/v2/dm/post",
                        "oid=" + aid + "&msg=" + Http.enc(text)
                                + "&segment=1000&type=1&size=25&color=16777215&bubble=0"
                                + "&player=1&ts=" + now + "&csrf=" + Http.enc(jct()));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /* ------------------------- 扫码登录 (TV/手机通用) ------------------------- */

    public void qrGenerate(final CB cb) {
        HttpUrl url = HttpUrl.parse("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code").newBuilder()
                .addQueryParameter("appkey", WEB_APPKEY)
                .addQueryParameter("local_id", "0")
                .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                .build();
        getSignedJson(url.toString(), WEB_SECRET, (data, err) -> {
            if (err != null) { post(cb, null, err); return; }
            try {
                JSONObject d = data.getJSONObject("data");
                JSONObject out = new JSONObject();
                out.put("url", d.getString("url"));
                out.put("auth_code", d.getString("auth_code"));
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 轮询扫码状态；code==0 表示成功并已写入 Cookie */
    public void qrPoll(final String authCode, final CB cb) {
        java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
        map.put("appkey", WEB_APPKEY);
        map.put("auth_code", authCode);
        map.put("local_id", "0");
        map.put("ts", String.valueOf(System.currentTimeMillis() / 1000));
        String sgn = sign(map, WEB_SECRET);
        FormBody body = new FormBody.Builder()
                .add("appkey", WEB_APPKEY)
                .add("auth_code", authCode)
                .add("local_id", "0")
                .add("ts", map.get("ts"))
                .add("sign", sgn)
                .build();
        Request req = new Request.Builder()
                .url("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
                .header("User-Agent", UA)
                .post(body).build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) { post(cb, null, e.getMessage()); }
            @Override public void onResponse(Call c, Response r) throws IOException {
                try {
                    JSONObject root = new JSONObject(r.body().string());
                    int code = root.optInt("code");
                    if (code == 0) {
                        JSONObject d = root.getJSONObject("data");
                        // 从响应头提取 bili_jct（评论/点赞等 POST 的 csrf 令牌）
                        String jct = "";
                        for (String ck : r.headers("Set-Cookie")) {
                            if (ck.startsWith("bili_jct=")) {
                                String v = ck.substring("bili_jct=".length());
                                int sc = v.indexOf(';');
                                jct = sc > 0 ? v.substring(0, sc) : v;
                            }
                        }
                        prefs.edit()
                                .putString("SESSDATA", d.getJSONObject("token_info").getString("sess_data"))
                                .putLong("bili_expire", d.getJSONObject("token_info").getLong("expire_time"))
                                .putLong("uid", d.getJSONObject("token_info").getLong("mid"))
                                .putString("bili_jct", jct)
                                .apply();
                        JSONObject out = new JSONObject();
                        out.put("status", 0);
                        out.put("mid", d.getJSONObject("token_info").getLong("mid"));
                        post(cb, out, null);
                    } else {
                        JSONObject out = new JSONObject();
                        out.put("status", code);           // 86038 未扫描 / 86039 已扫描未确认 / 86040 已过期
                        out.put("message", root.optString("message"));
                        post(cb, out, null);
                    }
                } catch (Exception e) { post(cb, null, e.toString()); }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* 内容接口                                                            */
    /* ------------------------------------------------------------------ */

    /** 热门视频：优先 web 综合热门，失败回退 app 端接口 */
    public void popular(final int page, final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson("https://api.bilibili.com/x/web-interface/popular?ps=20&pn=" + page);
                JSONArray list = root.getJSONArray("data");
                JSONObject out = new JSONObject(); out.put("list", list); post(cb, out, null);
            } catch (Exception e) {
                // 回退：旧 app 端热门
                try {
                    HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/show/popular/index").newBuilder()
                            .addQueryParameter("appkey", APP_APPKEY)
                            .addQueryParameter("type", "0")
                            .addQueryParameter("rid", "0")
                            .addQueryParameter("pn", String.valueOf(page))
                            .addQueryParameter("offset", "0")
                            .addQueryParameter("platform", "android")
                            .addQueryParameter("build", "6270500")
                            .addQueryParameter("mobi_app", "android")
                            .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                            .build();
                    java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                    for (int i = 0; i < url.querySize(); i++) map.put(url.queryParameterName(i), url.queryParameterValue(i));
                    String signed = url.newBuilder().addQueryParameter("sign", sign(map, APP_SECRET)).build().toString();
                    JSONObject root2 = Http.getJson(signed);
                    JSONObject d = root2.optJSONObject("data");
                    JSONArray list2 = d == null ? new JSONArray() : resolveList(d, "list");
                    JSONObject out2 = new JSONObject(); out2.put("list", list2 == null ? new JSONArray() : list2);
                    post(cb, out2, null);
                } catch (Exception e2) { post(cb, null, e2.toString()); }
            }
        });
    }

    /** 全站排行榜（web v2，失败回退 app） */
    public void ranking(String type, final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson("https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all");
                JSONArray list = root.getJSONObject("data").getJSONArray("list");
                JSONObject out = new JSONObject(); out.put("list", list); post(cb, out, null);
            } catch (Exception e) {
                try {
                    HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/ranking/type").newBuilder()
                            .addQueryParameter("appkey", APP_APPKEY)
                            .addQueryParameter("type_id", type)
                            .addQueryParameter("version", "2")
                            .addQueryParameter("platform", "android")
                            .addQueryParameter("build", "6270500")
                            .addQueryParameter("mobi_app", "android")
                            .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                            .build();
                    java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                    for (int i = 0; i < url.querySize(); i++) map.put(url.queryParameterName(i), url.queryParameterValue(i));
                    String signed = url.newBuilder().addQueryParameter("sign", sign(map, APP_SECRET)).build().toString();
                    JSONObject root2 = Http.getJson(signed);
                    JSONArray list2 = root2.getJSONObject("data").getJSONArray("list");
                    JSONObject out2 = new JSONObject(); out2.put("list", list2); post(cb, out2, null);
                } catch (Exception e2) { post(cb, null, e2.toString()); }
            }
        });
    }

    /** 搜索（web 精确搜索 type=video，失败回退 app 端） */
    public void search(final String keyword, final int page, final CB cb) {
        io.execute(() -> {
            try {
                String url = Wbi.sign("https://api.bilibili.com/x/web-interface/search/type?search_type=video"
                        + "&keyword=" + Http.enc(keyword) + "&page=" + page + "&pagesize=25");
                Request req = new Request.Builder().url(url)
                        .header("User-Agent", UA)
                        .header("Cookie", "buvid3=" + buvid3())
                        .build();
                okhttp3.Response r = client.newCall(req).execute();
                JSONObject root = new JSONObject(r.body().string()); r.close();
                if (root.optInt("code") != 0) throw new Exception(root.optString("message"));
                JSONArray result = root.getJSONObject("data").optJSONArray("result");
                JSONObject out = new JSONObject(); out.put("list", result == null ? new JSONArray() : result);
                post(cb, out, null);
            } catch (Exception e) {
                try {
                    HttpUrl hu = HttpUrl.parse("https://app.bilibili.com/x/v2/search/term/keywords").newBuilder()
                            .addQueryParameter("appkey", APP_APPKEY)
                            .addQueryParameter("term", keyword)
                            .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                            .build();
                    java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                    for (int i = 0; i < hu.querySize(); i++) map.put(hu.queryParameterName(i), hu.queryParameterValue(i));
                    String signed = hu.newBuilder().addQueryParameter("sign", sign(map, APP_SECRET)).build().toString();
                    JSONObject root2 = Http.getJson(signed);
                    JSONArray list2 = root2.getJSONObject("data").optJSONArray("terms");
                    JSONObject out2 = new JSONObject(); out2.put("list", list2 == null ? new JSONArray() : list2);
                    post(cb, out2, null);
                } catch (Exception e2) { post(cb, null, e2.toString()); }
            }
        });
    }

    /** 简单持久化/生成 buvid3（搜索接口需要 cookie） */
    private String buvid3() {
        String b = prefs.getString("buvid3", "");
        if (b.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            java.util.Random rnd = new java.util.Random();
            String hexc = "0123456789ABCDEF";
            for (int i = 0; i < 32; i++) sb.append(hexc.charAt(rnd.nextInt(16)));
            sb.insert(20, "-").insert(12, "-").insert(4, "-");
            b = sb + "infoc";
            prefs.edit().putString("buvid3", b).apply();
        }
        return b;
    }

    /** 我的收藏夹列表（web 接口，SESSDATA cookie 鉴权） */
    public void favFolderList(final CB cb) {
        io.execute(() -> {
            try {
                long my = uid();
                if (my == 0) { post(cb, null, "请先登录"); return; }
                JSONObject root = Http.getJson("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + my);
                JSONArray list = root.optJSONObject("data") == null
                        ? new JSONArray() : root.getJSONObject("data").optJSONArray("list");
                JSONObject out = new JSONObject(); out.put("list", list == null ? new JSONArray() : list);
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 最近观看历史（web 接口，cursor 翻页简化为 max 递减） */
    public void history(final int max, final CB cb) {
        io.execute(() -> {
            try {
                String url = "https://api.bilibili.com/x/web-interface/history/cursor?max=" + max
                        + "&page_size=20&business=archive";
                JSONObject root = Http.getJson(Wbi.sign(url));
                JSONObject d = root.optJSONObject("data");
                JSONArray list = d == null ? new JSONArray() : d.optJSONArray("list");
                JSONObject out = new JSONObject(); out.put("list", list == null ? new JSONArray() : list);
                out.put("next_max", d == null ? 0 : d.optLong("cursor", java.util.Collections.<String>emptyList().isEmpty() ? 0 : 0));
                // cursor 是对象
                if (d != null && d.optJSONObject("cursor") != null)
                    out.put("next_max", d.getJSONObject("cursor").optLong("max"));
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** UP 主投稿列表 */
    public void userVideos(long mid, int page, final CB cb) {
        HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/space/arc/search").newBuilder()
                .addQueryParameter("mid", String.valueOf(mid))
                .addQueryParameter("ps", "25")
                .addQueryParameter("pn", String.valueOf(page))
                .addQueryParameter("order", "pubdate")
                .build();
        signedGet(url, APP_SECRET, "list.vlist", cb);
    }

    /** 视频详情（web view 接口，含分P/aid；失败回退 app 端） */
    public void videoDetail(final String bvid, final CB cb) {
        io.execute(() -> {
            try {
                HttpUrl hu = HttpUrl.parse("https://api.bilibili.com/x/web-interface/view").newBuilder()
                        .addQueryParameter("bvid", bvid).build();
                JSONObject root = Http.getJson(hu.toString());
                post(cb, root.getJSONObject("data"), null);
            } catch (Exception e) {
                try {
                    HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/view").newBuilder()
                            .addQueryParameter("bvid", bvid)
                            .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                            .addQueryParameter("appkey", APP_APPKEY).build();
                    java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                    for (int i = 0; i < url.querySize(); i++) map.put(url.queryParameterName(i), url.queryParameterValue(i));
                    String signed = url.newBuilder().addQueryParameter("sign", sign(map, APP_SECRET)).build().toString();
                    JSONObject root2 = Http.getJson(signed);
                    post(cb, root2.optJSONObject("data"), null);
                } catch (Exception e2) { post(cb, null, e2.toString()); }
            }
        });
    }

    /** 收藏文件夹内容（web 资源列表） */
    public void favContents(long mediaId, int pn, final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson("https://api.bilibili.com/x/v2/fav/mediad/list?media_id="
                        + mediaId + "&pn=" + pn + "&ps=20");
                JSONObject d = root.optJSONObject("data");
                JSONArray list = d == null ? new JSONArray() : d.optJSONArray("medias");
                JSONObject out = new JSONObject(); out.put("list", list == null ? new JSONArray() : list);
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 清空观看历史（web，需登录 + csrf） */
    public void clearHistory(final CB cb) {
        io.execute(() -> {
            try {
                String csrf = prefs.getString("bili_jct", "");
                Http.postForm("https://api.bilibili.com/x/v2/history/clear",
                        "csrf=" + Http.enc(csrf));
                post(cb, new JSONObject(), null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /* ------------------------------------------------------------------ */
    /* v2.0 新增：发现 / 动态 / 关系 / 评论 / 播放地址 / 弹幕 / 更新        */
    /* ------------------------------------------------------------------ */

    /** 发现-综合热门（web 接口，稳定） */
    public void hot(final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson("https://api.bilibili.com/x/web-interface/popular?ps=20&pn=" + nextSeq());
                JSONArray list = root.getJSONArray("data");
                JSONObject out = new JSONObject(); out.put("list", list); post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    private int seqPopular = 1;
    private synchronized int nextSeq() { return seqPopular++; }

    /** 排行榜 web 接口（rid: 0 全站, 1 番剧, 4 动画...） */
    public void rankingWeb(int rid, final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson("https://api.bilibili.com/x/web-interface/ranking/v2?rid=" + rid + "&type=all");
                JSONArray list = root.getJSONObject("data").getJSONArray("list");
                JSONObject out = new JSONObject(); out.put("list", list); post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 关注动态（app 端 timeline，access_key 或 SESSDATA 均可） */
    public void dynamicMine(final String offset, final CB cb) {
        io.execute(() -> {
            try {
                HttpUrl hu = baseApp("https://app.bilibili.com/x/v2/feed/index").build();
                java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                for (int i = 0; i < hu.querySize(); i++) map.put(hu.queryParameterName(i), hu.queryParameterValue(i));
                map.put("idx", offset == null || offset.isEmpty() ? "0" : offset);
                map.put("feature", "0");
                map.put("column", "1");
                String qs = join(map);
                map.put("sign", sign(qs, APP_SECRET));
                JSONObject root = Http.getJson("https://app.bilibili.com/x/v2/feed/index?" + join(map));
                JSONObject d = root.optJSONObject("data");
                JSONArray items = d == null ? new JSONArray() : d.optJSONArray("items");
                JSONObject out = new JSONObject();
                out.put("items", items == null ? new JSONArray() : items);
                out.put("offset", d == null ? "" : String.valueOf(d.optLong("max_dynamic_id")));
                out.put("has_more", items != null && items.length() > 0);
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 指定用户的公开动态（可查看别人的） */
    public void dynamicOfUser(long hostMid, final String offset, final CB cb) {
        io.execute(() -> {
            try {
                String url = Wbi.sign("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid=" + hostMid
                        + "&offset=" + Http.enc(offset == null ? "" : offset) + "&timezone_offset=-480&features=itemOpusStyle");
                JSONObject root = Http.getJson(url);
                JSONObject data = root.optJSONObject("data");
                JSONObject out = new JSONObject();
                out.put("items", data == null ? new JSONArray() : data.optJSONArray("items"));
                out.put("offset", data == null ? "" : data.optString("offset", ""));
                out.put("has_more", data != null && data.optBoolean("has_more", false));
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 关注列表 */
    public void followingList(long mid, int pn, final CB cb) {
        relationList("following", mid, pn, cb);
    }

    /** 粉丝列表 */
    public void followersList(long mid, int pn, final CB cb) {
        relationList("follower", mid, pn, cb);
    }

    private void relationList(String kind, long mid, int pn, final CB cb) {
        io.execute(() -> {
            try {
                // 自己的关系列表走老接口；他人粉丝走 wbi 新接口
                String url;
                boolean self = uid() == mid;
                if (kind.equals("following")) {
                    url = "https://api.bilibili.com/x/relation/followings?vmid=" + mid + "&pn=" + pn + "&ps=25";
                    if (!self) url += "&order=desc";
                } else {
                    url = "https://api.bilibili.com/x/relation/followers?vmid=" + mid + "&pn=" + pn + "&ps=25";
                }
                url = Wbi.sign(url);
                JSONObject root = Http.getJson(url);
                JSONObject data = root.optJSONObject("data");
                JSONObject out = new JSONObject();
                out.put("list", data == null ? new JSONArray() : data.optJSONArray("list"));
                out.put("total", data == null ? 0 : data.optInt("total"));
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 用户信息卡片（昵称/头像/等级/签名/关注粉丝数） */
    public void userInfoCard(long mid, final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson(
                        "https://api.bilibili.com/x/web-interface/card?mid=" + mid + "&photo=false");
                JSONObject card = root.getJSONObject("data").getJSONObject("card");
                JSONObject out = new JSONObject();
                out.put("name", card.getString("name"));
                out.put("face", card.getString("face"));
                out.put("sign", card.optString("sign"));
                out.put("level", card.optInt("level_info", 0) > 0
                        ? card.getJSONObject("level_info").optInt("current_level") : card.optInt("rank"));
                out.put("fans", card.optJSONObject("fans") != null ? card.optJSONObject("fans").optInt("fans")
                        : root.getJSONObject("data").optInt("fans", card.optInt("fans")));
                out.put("attentions", card.optInt("attentions"));
                out.put("mid", card.optLong("mid"));
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** UP 主投稿（wbi 空间接口，带兜底） */
    public void spaceArcSearch(long mid, int pn, final CB cb) {
        io.execute(() -> {
            try {
                String url = Wbi.sign("https://api.bilibili.com/x/space/wbi/arc/search?mid=" + mid
                        + "&ps=25&pn=" + pn + "&order=pubdate");
                JSONObject root = Http.getJson(url);
                JSONArray list = root.getJSONObject("data").getJSONObject("list").getJSONArray("vlist");
                JSONObject out = new JSONObject(); out.put("list", list); post(cb, out, null);
            } catch (Exception e) {
                // 兜底：老接口
                try {
                    HttpUrl hu = HttpUrl.parse("https://api.bilibili.com/x/space/arc/search").newBuilder()
                            .addQueryParameter("mid", String.valueOf(mid))
                            .addQueryParameter("ps", "25")
                            .addQueryParameter("pn", String.valueOf(pn)).build();
                    JSONObject root = Http.getJson(hu.toString());
                    JSONArray list = root.getJSONObject("data").getJSONObject("list").getJSONArray("vlist");
                    JSONObject out = new JSONObject(); out.put("list", list); post(cb, out, null);
                } catch (Exception e2) { post(cb, null, e2.toString()); }
            }
        });
    }

    /** 视频评论（一级）。data: {upper:{top}, replies[], page:{next}} */
    public void comments(long aid, String bvid, int pn, final CB cb) {
        io.execute(() -> {
            try {
                String url = "https://api.bilibili.com/x/v2/reply/main?type=1&mode=3"
                        + (bvid != null && bvid.startsWith("BV") ? "&oid=" + Http.enc(bvid) : "&oid=" + aid)
                        + "&next=" + pn;
                JSONObject root = Http.getJson(Wbi.sign(url));
                JSONObject out = root.optJSONObject("data") != null ? root.getJSONObject("data") : new JSONObject();
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 获取真实可播地址（durl 直链，可直接喂给系统播放器/VideoView）。qn 由设置决定。 */
    public void playUrls(String bvid, long aid, final CB cb) {
        io.execute(() -> {
            try {
                HttpUrl hu = baseApp("https://api.bilibili.com/x/player/playurl").build();
                java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                for (int i = 0; i < hu.querySize(); i++) map.put(hu.queryParameterName(i), hu.queryParameterValue(i));
                map.put("bvid", bvid != null ? bvid : "");
                map.put("aid", String.valueOf(aid));
                map.put("qn", String.valueOf(qualityQn()));
                map.put("fnval", "1");   // durl mp4 直链（无 DASH，兼容 4.4 系统播放器）
                map.put("fourk", "1");
                map.put("high_title", "");
                String qs = join(map);
                map.put("sign", sign(qs, APP_SECRET));
                String url = "https://api.bilibili.com/x/player/playurl?" + join(map);
                JSONObject root = Http.getJson(url);
                JSONObject out = root.getJSONObject("data");
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    /** 画质切换：设置里保存 qn（16=720P, 32=1080P, 64=1080P高码率, 80=1080P+需大会员, 112=4K） */
    public int qualityQn() { return prefs.getInt("quality_qn", 32); }
    public void setQualityQn(int qn) { prefs.edit().putInt("quality_qn", qn).apply(); }

    /** 弹幕 XML（压缩与否由服务器决定，此处按未压缩解析；数量限制 8000） */
    public void danmaku(long aid, final CB cb) {
        io.execute(() -> {
            try {
                Request req = new Request.Builder()
                        .url("https://comment.bilibili.com/" + aid + ".xml")
                        .header("User-Agent", UA).build();
                okhttp3.Response r = client.newCall(req).execute();
                byte[] bytes = r.body().bytes();
                r.close();
                String xml;
                try {
                    java.io.ByteArrayInputStream bin = new java.io.ByteArrayInputStream(bytes);
                    java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(bin);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192]; int n;
                    while ((n = gz.read(buf)) > 0) bos.write(buf, 0, n);
                    xml = new String(bos.toByteArray(), "UTF-8");
                } catch (Exception notGzip) {
                    xml = new String(bytes, "UTF-8");
                }
                java.util.List<JSONObject> list = new ArrayList<>();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("<d p=\"([^\"]+)\">([^<]*)</d>").matcher(xml);
                int count = 0;
                while (m.find() && count < 8000) {
                    String[] p = m.group(1).split(",");
                    JSONObject d = new JSONObject();
                    d.put("time", Double.parseDouble(p[0]));
                    d.put("mode", Integer.parseInt(p[1]));
                    d.put("size", Integer.parseInt(p[2]));
                    d.put("color", Long.parseLong(p[3]));
                    d.put("text", decodeHtml(m.group(4)));
                    list.add(d);
                    count++;
                }
                JSONArray arr = new JSONArray();
                for (JSONObject o : list) arr.put(o);
                JSONObject out = new JSONObject(); out.put("list", arr);
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    private static String decodeHtml(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("\n", " ");
    }

    /** 上报观看历史（模拟客户端行为，部分接口依赖） */
    public void pushHistory(String bvid, long aid) {
        io.execute(() -> {
            try {
                HttpUrl hu = baseApp("https://api.bilibili.com/x/v2/history/report").build();
                java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
                for (int i = 0; i < hu.querySize(); i++) map.put(hu.queryParameterName(i), hu.queryParameterValue(i));
                map.put("aid", String.valueOf(aid));
                map.put("bvid", bvid == null ? "" : bvid);
                map.put("part", "1");
                map.put("progress", "1");
                map.put("platform", "android");
                String qs = join(map);
                map.put("sign", sign(qs, APP_SECRET));
                Http.postForm("https://api.bilibili.com/x/v2/history/report", join(map));
            } catch (Exception ignore) {}
        });
    }

    /** 检查更新：查询 GitHub Releases（redgtab/QwenCoder） */
    public void checkUpdate(final CB cb) {
        io.execute(() -> {
            try {
                JSONObject root = Http.getJson(
                        "https://api.github.com/repos/redgtab/QwenCoder/releases/latest");
                JSONObject out = new JSONObject();
                out.put("tag", root.getString("tag_name"));
                out.put("name", root.optString("name"));
                out.put("body", root.optString("body"));
                JSONArray assets = root.optJSONArray("assets");
                String apk = "";
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String n = a.optString("name");
                    if (n.endsWith(".apk")) { apk = a.getString("browser_download_url"); break; }
                }
                out.put("apk", apk);
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    private static String join(java.util.SortedMap<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(Http.enc(e.getValue()));
        }
        return sb.toString();
    }

    /** 使用已排序参数串计算 sign（供 playurl 等复用） */
    private static String sign(String queryJoined, String secret) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] dig = md.digest((queryJoined + secret).getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : dig) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /* ------------------------------------------------------------------ */
    /* 内部工具                                                            */
    /* ------------------------------------------------------------------ */

    private HttpUrl.Builder baseApp(String raw) {
        return HttpUrl.parse(raw).newBuilder()
                .addQueryParameter("appkey", APP_APPKEY)
                .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000));
    }

    private static java.util.SortedMap<String, String> params(String appkey, String query) {
        java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
        map.put("appkey", appkey);
        for (String kv : query.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2) map.put(p[0], p[1]);
        }
        return map;
    }

    /** md5(query + secret) —— B 站公开签名算法 */
    private static String sign(java.util.SortedMap<String, String> map, String secret) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append(secret);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(sb.toString().getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : dig) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void signedGet(HttpUrl url, String secret, String listField, final CB cb) {
        java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
        for (int i = 0; i < url.querySize(); i++) map.put(url.queryParameterName(i), url.queryParameterValue(i));
        String s = sign(map, secret);
        HttpUrl signed = url.newBuilder().addQueryParameter("sign", s).build();
        getJson(signed.toString(), null, (data, err) -> {
            if (err != null) { post(cb, null, err); return; }
            try {
                checkCode(data);
                JSONObject out = new JSONObject();
                JSONArray arr = resolveList(data.optJSONObject("data"), listField);
                out.put("list", arr == null ? new JSONArray() : arr);
                post(cb, out, null);
            } catch (Exception e) { post(cb, null, e.toString()); }
        });
    }

    private void signedGetRaw(HttpUrl url, String secret, final CB cb) {
        java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
        for (int i = 0; i < url.querySize(); i++) map.put(url.queryParameterName(i), url.queryParameterValue(i));
        String s = sign(map, secret);
        getJson(url.newBuilder().addQueryParameter("sign", s).build().toString(), null, cb);
    }

    private static JSONArray resolveList(JSONObject data, String field) {
        if (data == null) return null;
        if (field.contains(".")) {
            String[] parts = field.split("\\.");
            JSONObject cur = data;
            for (int i = 0; i < parts.length - 1 && cur != null; i++) cur = cur.optJSONObject(parts[i]);
            return cur == null ? null : cur.optJSONArray(parts[parts.length - 1]);
        }
        JSONArray arr = data.optJSONArray(field);
        if (arr != null) return arr;
        JSONObject sub = data.optJSONObject(field);
        if (sub != null) return sub.optJSONArray("list");
        // 兜底：直接找第一个数组字段
        java.util.Iterator<String> it = data.keys();
        while (it.hasNext()) {
            Object o = data.opt(it.next());
            if (o instanceof JSONArray) return (JSONArray) o;
        }
        return null;
    }

    private static void checkCode(JSONObject root) throws Exception {
        int code = root.optInt("code", 0);
        if (code != 0) throw new Exception("API code=" + code + " " + root.optString("message"));
    }

    private void getSignedJson(String url, String secret, final CB cb) {
        // url 中已含参数，需补 sign
        HttpUrl hu = HttpUrl.parse(url);
        java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
        for (int i = 0; i < hu.querySize(); i++) map.put(hu.queryParameterName(i), hu.queryParameterValue(i));
        String signed = hu.newBuilder().addQueryParameter("sign", sign(map, secret)).build().toString();
        getJson(signed, null, cb);
    }

    private void getJson(String url, java.util.Map<String, String> headers, final CB cb) {
        Request.Builder rb = new Request.Builder().url(url).header("User-Agent", UA);
        if (headers != null) for (java.util.Map.Entry<String, String> e : headers.entrySet()) rb.header(e.getKey(), e.getValue());
        client.newCall(rb.build()).enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) { post(cb, null, e.getMessage()); }
            @Override public void onResponse(Call c, Response r) throws IOException {
                try {
                    JSONObject root = new JSONObject(r.body().string());
                    checkCode(root);
                    post(cb, root, null);
                } catch (Exception e) {
                    post(cb, null, e.toString());
                }
            }
        });
    }

    private void post(CB cb, JSONObject data, String err) {
        final CB f = cb; final JSONObject d = data; final String e = err;
        main.post(() -> f.onResult(d, e));
    }

    /** 简单持久化 CookieJar */
    private static class PersistCookieJar implements CookieJar {
        private final SharedPreferences prefs;
        private final java.util.Map<String, java.util.List<Cookie>> store =
                new java.util.HashMap<>();

        PersistCookieJar(SharedPreferences prefs) {
            this.prefs = prefs;
            java.util.List<Cookie> cs = new java.util.ArrayList<>();
            String sess = prefs.getString("SESSDATA", "");
            if (!sess.isEmpty()) cs.add(new Cookie.Builder().name("SESSDATA").value(sess)
                    .domain("bilibili.com").path("/").build());
            String jct = prefs.getString("bili_jct", "");
            if (!jct.isEmpty()) cs.add(new Cookie.Builder().name("bili_jct").value(jct)
                    .domain("bilibili.com").path("/").build());
            if (!cs.isEmpty()) {
                put("app.bilibili.com", cs);
                put("api.bilibili.com", cs);
                put("www.bilibili.com", cs);
                put("passport.bilibili.com", cs);
                put("comment.bilibili.com", cs);
                put("dynamic.bilibili.com", cs);
            }
        }

        private void put(String host, java.util.List<Cookie> cs) { store.put(host, cs); }

        @Override
        public void saveFromResponse(HttpUrl url, java.util.List<Cookie> cookies) {
            String host = url.host();
            java.util.List<Cookie> old = store.get(host);
            java.util.List<Cookie> merged = new java.util.ArrayList<>();
            if (old != null) for (Cookie c : old)
                if (!contains(cookies, c.name())) merged.add(c);
            merged.addAll(cookies);
            store.put(host, merged);
            SharedPreferences.Editor ed = prefs.edit();
            for (Cookie c : cookies) {
                if (c.name().equals("SESSDATA")) ed.putString("SESSDATA", c.value());
                if (c.name().equals("bili_jct")) ed.putString("bili_jct", c.value());
            }
            ed.apply();
        }

        private static boolean contains(java.util.List<Cookie> cs, String name) {
            for (Cookie c : cs) if (c.name().equals(name)) return true;
            return false;
        }

        @Override
        public java.util.List<Cookie> loadForRequest(HttpUrl url) {
            java.util.List<Cookie> cs = store.get(url.host());
            return cs == null ? java.util.Collections.<Cookie>emptyList() : cs;
        }
    }
}
