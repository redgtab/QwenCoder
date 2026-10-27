package com.bililite.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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

    private static final String UA =
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

    /** 获取当前登录用户信息 -> {name, mid, face} */
    public void myInfo(final CB cb) {
        getJson("https://app.bilibili.com/x/v2/myinfo", null, (data, err) -> {
            if (err != null) { post(cb, null, err); return; }
            try {
                JSONObject user = data.getJSONObject("user");
                prefs.edit()
                        .putString("uname", user.getString("uname"))
                        .putLong("uid", user.getLong("mid"))
                        .putString("face", user.optString("face"))
                        .apply();
                JSONObject out = new JSONObject();
                out.put("name", user.getString("uname"));
                out.put("mid", user.getLong("mid"));
                out.put("face", user.optString("face"));
                post(cb, out, null);
            } catch (Exception e) {
                post(cb, null, e.toString());
            }
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
        FormBody body = new FormBody.Builder()
                .add("appkey", WEB_APPKEY)
                .add("auth_code", authCode)
                .add("local_id", "0")
                .add("ts", String.valueOf(System.currentTimeMillis() / 1000))
                .add("sign", sign(params(WEB_APPKEY, "auth_code=" + authCode + "&local_id=0"), WEB_SECRET))
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
                        prefs.edit()
                                .putString("SESSDATA", d.getJSONObject("token_info").getString("sess_data"))
                                .putLong("bili_expire", d.getJSONObject("token_info").getLong("expire_time"))
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

    /** 热门视频（分页） */
    public void popular(int page, final CB cb) {
        HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/show/popular/index").newBuilder()
                .addQueryParameter("appkey", APP_APPKEY)
                .addQueryParameter("type", "0")
                .addQueryParameter("rid", "0")
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("ver", "0")
                .addQueryParameter("platform", "android")
                .addQueryParameter("scale", "1080")
                .addQueryParameter("build", "6270500")
                .addQueryParameter("mobi_app", "android")
                .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                .build();
        signedGet(url, APP_SECRET, "list", cb);
    }

    /** 全站排行榜（综合 / 影视 / 番剧… type: all, fc, anime ...） */
    public void ranking(String type, final CB cb) {
        HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/ranking/type").newBuilder()
                .addQueryParameter("appkey", APP_APPKEY)
                .addQueryParameter("type_id", type)
                .addQueryParameter("version", "2")
                .addQueryParameter("platform", "android")
                .addQueryParameter("build", "6270500")
                .addQueryParameter("mobi_app", "android")
                .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                .build();
        signedGet(url, APP_SECRET, "list", cb);
    }

    /** 搜索（app 端 wbi 老接口，无需签名校验的公开版） */
    public void search(String keyword, int page, final CB cb) {
        HttpUrl url = HttpUrl.parse("https://app.bilibili.com/x/v2/search/all/inline").newBuilder()
                .addQueryParameter("appkey", APP_APPKEY)
                .addQueryParameter("keyword", keyword)
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("pagesize", "20")
                .addQueryParameter("platform", "android")
                .addQueryParameter("build", "6270500")
                .addQueryParameter("mobi_app", "android")
                .addQueryParameter("ts", String.valueOf(System.currentTimeMillis() / 1000))
                .build();
        signedGet(url, APP_SECRET, "result", cb);
    }

    /** 我的收藏（需要登录） */
    public void favFolderList(final CB cb) {
        HttpUrl url = baseApp("https://app.bilibili.com/x/v2/collections").newBuilder()
                .addQueryParameter("platform", "android")
                .addQueryParameter("build", "6270500")
                .addQueryParameter("mobi_app", "android")
                .addQueryParameter("access_key", prefs.getString("access_key", ""))
                .build();
        signedGet(url, APP_SECRET, "list", cb);
    }

    /** 最近观看历史（需要登录） */
    public void history(final CB cb) {
        HttpUrl url = baseApp("https://app.bilibili.com/x/v2/history").newBuilder()
                .addQueryParameter("platform", "android")
                .addQueryParameter("build", "6270500")
                .addQueryParameter("mobi_app", "android")
                .addQueryParameter("access_key", prefs.getString("access_key", ""))
                .build();
        signedGet(url, APP_SECRET, "list", cb);
    }

    /** UP 主投稿列表 */
    public void userVideos(long mid, int page, final CB cb) {
        HttpUrl url = baseApp("https://app.bilibili.com/x/v2/space/arc/search").newBuilder()
                .addQueryParameter("mid", String.valueOf(mid))
                .addQueryParameter("ps", "25")
                .addQueryParameter("pn", String.valueOf(page))
                .addQueryParameter("order", "pubdate")
                .build();
        signedGet(url, APP_SECRET, "list.vlist", cb);
    }

    /** 视频详情（分 P 信息） */
    public void videoDetail(String bvid, final CB cb) {
        HttpUrl url = baseApp("https://app.bilibili.com/x/v2/view").newBuilder()
                .addQueryParameter("bvid", bvid)
                .build();
        signedGetRaw(url, APP_SECRET, cb);
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
            String sess = prefs.getString("SESSDATA", "");
            if (!sess.isEmpty()) {
                Cookie c = new Cookie.Builder().name("SESSDATA").value(sess)
                        .domain("bilibili.com").path("/").build();
                put("app.bilibili.com", java.util.Collections.singletonList(c));
                put("api.bilibili.com", java.util.Collections.singletonList(c));
                put("www.bilibili.com", java.util.Collections.singletonList(c));
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
