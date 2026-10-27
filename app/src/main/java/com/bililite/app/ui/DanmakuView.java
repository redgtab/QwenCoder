package com.bililite.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 极简滚动弹幕视图（不依赖 ijkplayer）：
 * VideoView 播放时叠在本 View 上，本 View 依据视频进度时间轴渲染滚过/顶部弹幕。
 */
public class DanmakuView extends View {

    public static class DM {
        double time; int mode; int size; int color; String text;
        float x, y; boolean shown;
    }

    private final List<DM> all = new ArrayList<>();
    private final List<DM> active = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final int[] laneY = new int[12];
    private boolean lanesInit;
    private double posSec;             // 当前播放秒数（外部每秒更新）
    private double lastTick;           // 上次刷新时刻(系统毫秒/1000)
    private boolean enabled = true;
    private float speedFactor = 1f;    // 性能优化：低端设备可调小同屏数量

    private Runnable ticker;

    public DanmakuView(Context ctx) { this(ctx, null); }
    public DanmakuView(Context ctx, AttributeSet attrs) { super(ctx, attrs); }

    public void setData(List<DM> list) {
        all.clear();
        active.clear();
        if (list != null) all.addAll(list);
        java.util.Collections.sort(all, (a, b) -> Double.compare(a.time, b.time));
    }

    public void setEnabled(boolean en) {
        enabled = en;
        if (!en) active.clear();
        invalidate();
    }

    /** 外部每 ~500ms 调用一次：videoPosSec = MediaController.getCurrentPosition()/1000 */
    public void seekTo(double sec) {
        posSec = sec;
        if (!lanesInit && getHeight() > 0) {
            for (int i = 0; i < laneY.length; i++) laneY[i] = 30 + i * (getHeight() - 60) / laneY.length;
            lanesInit = true;
        }
        lastTick = System.currentTimeMillis() / 1000.0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!enabled || getWidth() == 0) return;

        double now = System.currentTimeMillis() / 1000.0;
        double dt = Math.min(1.0, now - lastTick);
        lastTick = now;
        double advance = dt * (getWidth() / 8.0) * speedFactor; // 8 秒横穿屏幕

        // 生成新弹幕（posSec ±0.5s 内）
        Iterator<DM> it = all.iterator();
        while (it.hasNext()) {
            DM d = it.next();
            if (d.time > posSec + 0.6) break;
            if (!d.shown && d.time <= posSec + 0.3) {
                d.shown = true;
                it.remove();
                if (active.size() < 60) {          // 同屏上限，保性能
                    d.x = getWidth();
                    d.y = laneY[rnd.nextInt(laneY.length)];
                    d.size = d.size <= 0 ? 25 : Math.max(18, Math.min(40, d.size));
                    active.add(d);
                }
            }
        }

        paint.setTextSize(25);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        Rect bounds = new Rect();

        Iterator<DM> ai = active.iterator();
        while (ai.hasNext()) {
            DM d = ai.next();
            d.x -= (float) advance;
            if (d.x < -200) { ai.remove(); continue; }
            paint.setStyle(Paint.Style.FILL);
            int c = d.color == 0 ? Color.WHITE : (0xFF000000 | d.color);
            if (c == 0xFFFFFFFF) c = Color.WHITE;
            paint.setColor(c);
            paint.getTextBounds(d.text, 0, d.text.length(), bounds);
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0x80000000);
            canvas.drawText(d.text, d.x + 1, d.y + 1, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(c);
            canvas.drawText(d.text, d.x, d.y, paint);
        }

        if (!all.isEmpty() || !active.isEmpty()) postInvalidateDelayed(120);
    }

    public DM parse(JSONObject o) {
        try {
            DM d = new DM();
            d.time = o.getDouble("time");
            d.mode = o.getInt("mode");
            d.size = o.getInt("size");
            d.color = (int) o.getLong("color");
            d.text = o.getString("text");
            return d;
        } catch (Exception e) { return null; }
    }

    public int count() { return all.size() + active.size(); }

    /** 追加弹幕（如自己刚发送的），保持时间有序 */
    public void append(java.util.List<DM> more) {
        if (more == null) return;
        all.addAll(more);
        java.util.Collections.sort(all, (a, b) -> Double.compare(a.time, b.time));
        invalidate();
    }

    public void setSpeedFactor(float f) { this.speedFactor = f; }
}
