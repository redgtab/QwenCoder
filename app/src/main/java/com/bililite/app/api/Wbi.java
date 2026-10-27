package com.bililite.app.api;

import org.json.JSONObject;

import java.security.MessageDigest;

/**
 * B 站 Web 端 WBI 签名（参考 bbll / NewBV 等项目实现）。
 * 用于 x/reply、x/space/wbi/arc/search、关系粉丝列表等需要 wbi 签名的接口。
 */
public class Wbi {

    private static final int[] MIXIN_KEY_ENC_TAB = new int[]{
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 1, 29, 14, 0, 26, 30, 34, 4, 13, 20, 21, 36, 37, 25, 12, 24,
            40, 12, 11, 22, 44, 17, 55, 6, 16, 38, 56, 57, 52, 51, 54, 59, 48, 41, 4, 5,
            6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
            26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63};

    private static long sCacheTime;
    private static String sImgKey, sSubKey;

    /** 获取（并缓存 10 分钟）wbi 密钥 */
    public static synchronized void refresh(BiliApi api) {
        long now = System.currentTimeMillis();
        if (sImgKey != null && now - sCacheTime < 10 * 60 * 1000) return;
        try {
            JSONObject root = Http.getJson("https://api.bilibili.com/x/web-interface/nav");
            JSONObject p = root.getJSONObject("data").getJSONObject("wbi_img");
            sImgKey = keyFromUrl(p.getString("img_url"));
            sSubKey = keyFromUrl(p.getString("sub_url"));
            sCacheTime = now;
        } catch (Exception ignore) {
        }
    }

    private static String keyFromUrl(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String mixinKey() {
        String raw = sImgKey + sSubKey;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32 && i < raw.length(); i++) {
            int idx = MIXIN_KEY_ENC_TAB[i];
            if (idx >= 0 && idx < raw.length()) sb.append(raw.charAt(idx));
        }
        return sb.toString();
    }

    /** 给查询串加 wts & w_rid。返回完整 URL。若密钥不可用则仅加 wts。 */
    public static String sign(String urlWithQuery) {
        refresh(null);
        try {
            int q = urlWithQuery.indexOf('?');
            String query = q >= 0 ? urlWithQuery.substring(q + 1) : "";
            java.util.SortedMap<String, String> map = new java.util.TreeMap<>();
            for (String kv : query.split("&")) {
                String[] p = kv.split("=", 2);
                if (p.length == 2) {
                    String v = java.net.URLDecoder.decode(p[1], "UTF-8");
                    v = v.replaceAll("[!'\\(\\)*]", ""); // wbi 规则：过滤特殊字符
                    map.put(p[0], v);
                }
            }
            long wts = System.currentTimeMillis() / 1000;
            map.put("wts", String.valueOf(wts));
            StringBuilder qs = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : map.entrySet()) {
                if (qs.length() > 0) qs.append('&');
                qs.append(e.getKey()).append('=').append(Http.enc(e.getValue()));
            }
            if (sImgKey == null) {
                return urlWithQuery + (q >= 0 ? "&" : "?") + "wts=" + wts;
            }
            String wrid = md5(qs + mixinKey());
            return urlWithQuery + (q >= 0 ? "&" : "?") + "wts=" + wts + "&w_rid=" + wrid;
        } catch (Exception e) {
            return urlWithQuery;
        }
    }

    static String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] dig = md.digest(s.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : dig) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
