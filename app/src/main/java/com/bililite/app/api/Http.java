package com.bililite.app.api;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 同步 HTTP 工具（仅在 BiliApi 的 IO 线程池中调用） */
class Http {

    static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded");
    static final String UA = BiliApi.UA;

    private static OkHttpClient main0;

    static synchronized OkHttpClient client() {
        if (main0 == null) {
            main0 = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .build();
        }
        return main0;
    }

    /** GET -> JSON；失败抛 IOException（带服务器 message） */
    public static JSONObject getJson(String url) throws Exception {
        Request req = new Request.Builder().url(url).header("User-Agent", UA).build();
        try (Response r = client().newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "";
            return parse(body);
        }
    }

    /** POST form -> JSON；失败抛 IOException */
    static JSONObject postForm(String url, String form) throws Exception {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .post(RequestBody.create(FORM, form)).build();
        try (Response r = client().newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "";
            return parse(body);
        }
    }

    private static JSONObject parse(String body) throws Exception {
        if (body == null || body.trim().isEmpty()) throw new Exception("empty response");
        JSONObject root = new JSONObject(body);
        int code = root.optInt("code", 0);
        if (code != 0 && code != -100) { // -100 由上层特殊处理
            throw new Exception("API code=" + code + " " + root.optString("message"));
        }
        return root;
    }

    static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
