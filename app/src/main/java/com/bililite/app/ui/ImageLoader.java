package com.bililite.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片加载器（性能优化版）：
 *  - LruCache 内存缓存（1/8 堆）
 *  - 磁盘缓存（10MB 上限，自动淘汰）
 *  - RGB_565 + inSampleSize 降采样解码，省内存
 *  - 4 线程池 + 主线程回调；相同请求去重；RecyclerView 复用校验 tag
 */
public class ImageLoader {

    private static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount(); }
    };
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static File diskDir;
    private static final java.util.Set<String> inflight =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public static synchronized void init(Context ctx) {
        if (diskDir == null) {
            diskDir = new File(ctx.getCacheDir(), "img");
            if (!diskDir.exists()) diskDir.mkdirs();
        }
    }

    public static void load(ImageView iv, String url) { load(iv, url, 320); }

    /** targetW: 期望解码宽度（px），大图自动降采样 */
    public static void load(final ImageView iv, final String url, final int targetW) {
        if (iv == null) return;
        if (TextUtils.isEmpty(url)) { iv.setImageDrawable(null); return; }
        iv.setTag(url);
        final String key = key(url, targetW);
        Bitmap b = mem.get(key);
        if (b != null) { iv.setImageBitmap(b); return; }
        iv.setImageDrawable(null);
        if (!inflight.add(key)) return;   // 同图加载中，不重复发起
        pool.execute(() -> {
            Bitmap got = null;
            try {
                got = readDisk(key);
                if (got == null) {
                    got = fetchAndDecode(url, targetW);
                    if (got != null) writeDisk(key, got);
                }
            } catch (Exception ignored) { }
            inflight.remove(key);
            if (got != null) mem.put(key, got);
            final Bitmap fb = got;
            main.post(() -> {
                if (fb != null && url.equals(iv.getTag())) iv.setImageBitmap(fb);
            });
        });
    }

    private static String key(String url, int w) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest((url + "#" + w).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return String.valueOf((url + w).hashCode()); }
    }

    private static Bitmap fetchAndDecode(String url, int targetW) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        java.io.InputStream in = c.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        byte[] bytes = bos.toByteArray();
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
        int sample = 1;
        if (bounds.outWidth > targetW && bounds.outWidth > 0)
            sample = Math.max(1, bounds.outWidth / targetW);
        opt.inSampleSize = sample;
        opt.inPreferredConfig = Bitmap.Config.RGB_565;   // 半内存，封面足够
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opt);
    }

    private static Bitmap readDisk(String key) {
        if (diskDir == null) return null;
        File f = new File(diskDir, key);
        if (!f.exists()) return null;
        try {
            FileInputStream fin = new FileInputStream(f);
            byte[] bytes = new byte[(int) f.length()];
            int r = fin.read(bytes); fin.close();
            if (r <= 0) return null;
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (Exception e) { return null; }
    }

    private static void writeDisk(String key, Bitmap bmp) {
        if (diskDir == null) return;
        try {
            if (diskTotal() > 10L * 1024 * 1024) clearDiskCache();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            FileOutputStream fout = new FileOutputStream(new File(diskDir, key));
            fout.write(bos.toByteArray()); fout.close();
        } catch (Exception ignored) { }
    }

    private static long diskTotal() {
        File[] fs = diskDir.listFiles();
        long t = 0;
        if (fs != null) for (File f : fs) t += f.length();
        return t;
    }

    public static void clearDiskCache() {
        File[] fs = diskDir == null ? null : diskDir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        mem.evictAll();
    }
}
