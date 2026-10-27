package com.bililite.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bililite.app.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 极简图片加载器（避免引入 Glide 等对低版本不友好的库） */
public class ImageLoader {

    private static OkHttpClient client;
    private static final Map<String, Bitmap> cache = new HashMap<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newFixedThreadPool(2);

    public static void load(ImageView view, String url) {
        Context ctx = view.getContext();
        Drawable placeholder = ctx.getResources().getDrawable(R.drawable.ic_placeholder);
        if (TextUtils.isEmpty(url)) { view.setImageDrawable(placeholder); return; }
        Bitmap hit = cache.get(url);
        if (hit != null) { view.setImageBitmap(hit); return; }
        view.setImageDrawable(placeholder);
        view.setTag(("tag:" + url).hashCode(), url); // 标记，防止列表复用错位
        fetch(ctx, url, view);
    }

    private static void fetch(final Context ctx, final String url, ImageView iv) {
        final WeakReference<ImageView> ref = new WeakReference<>(iv);
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build();
        }
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0").header("Referer", "https://www.bilibili.com").build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call c, IOException e) { }
            @Override public void onResponse(Call c, okhttp3.Response r) throws IOException {
                Bitmap bmp = null;
                try {
                    byte[] data = r.body().bytes();
                    bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
                } catch (Exception ignored) { }
                final Bitmap f = bmp;
                if (f == null) return;
                cache.put(url, f);
                main.post(() -> {
                    ImageView v = ref.get();
                    if (v != null && url.equals(v.getTag(("tag:" + url).hashCode()))) {
                        v.setImageBitmap(scale(f, 480));
                    }
                });
            }
        });
    }

    private static Bitmap scale(Bitmap src, int max) {
        if (src.getWidth() <= max && src.getHeight() <= max) return src;
        float ratio = Math.min((float) max / src.getWidth(), (float) max / src.getHeight());
        int w = Math.max(1, (int) (src.getWidth() * ratio));
        int h = Math.max(1, (int) (src.getHeight() * ratio));
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(src, null, new android.graphics.Rect(0, 0, w, h), null);
        return out;
    }
}
