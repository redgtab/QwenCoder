package com.bililite.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;
import com.bililite.app.api.VideoItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * TV 详情页 + 内置播放器（v2.0.2）：
 *  - VideoView 播放 durl mp4 直链（兼容 API19），弹幕 DanmakuView 叠加渲染；
 *  - 遥控器按键：OK/回车=播放暂停，左右=±10s，回放/快进键=±30s，菜单键=画质切换，
 *                MEDIA_PLAY_PAUSE/MEDIA_STOP 等媒体键支持；
 *  - 视频缓存目录：内部存储 output/bililite（见 BiliApi.videoCacheDir()）。
 */
public class PlayActivity extends AppCompatActivity {

    private String bvid, title, uploader, plays, danmaku, date, intro;
    private long aid, mid;
    private JSONArray pages; // 分 P

    private VideoView videoView;
    private DanmakuView danmakuView;
    private View playerBox;
    private TextView btnQuality, btnDmToggle;
    private final Handler handler = new Handler();
    private boolean danmakuOn = true;
    private Runnable tick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        Intent in = getIntent();
        bvid = in.getStringExtra("bvid");
        aid = in.getLongExtra("aid", 0);
        title = in.getStringExtra("title");
        uploader = in.getStringExtra("uploader");
        mid = in.getLongExtra("mid", 0);
        plays = in.getStringExtra("plays");
        danmaku = in.getStringExtra("danmaku");
        date = in.getStringExtra("date");
        intro = in.getStringExtra("intro");

        ((TextView) findViewById(R.id.tv_top_title)).setText(title);
        ((TextView) findViewById(R.id.tv_title)).setText(title);
        ((TextView) findViewById(R.id.tv_meta)).setText(
                ("UP: " + (uploader == null ? "" : uploader)) + "   " +
                        (plays == null ? "" : plays + " 播放 · ") +
                        (danmaku == null ? "" : danmaku + " 弹幕 · ") +
                        (date == null ? "" : date));
        ((TextView) findViewById(R.id.tv_intro)).setText(TextUtils.isEmpty(intro) ? "（无简介）" : intro);

        if (!TextUtils.isEmpty(uploader) && mid > 0) {
            findViewById(R.id.tv_meta).setOnClickListener(v -> {
                Intent sp = new Intent(this, UserSpaceActivity.class);
                sp.putExtra("mid", mid);
                sp.putExtra("uname", uploader);
                startActivity(sp);
            });
        }

        videoView = findViewById(R.id.video_view);
        danmakuView = findViewById(R.id.danmaku_view);
        playerBox = findViewById(R.id.player_box);
        btnQuality = findViewById(R.id.btn_quality);
        btnDmToggle = findViewById(R.id.btn_danmaku_toggle);

        BiliApi api = BiliApi.get(this);
        danmakuOn = api.prefsBool("danmaku_enabled", true);
        updateDmBtn();
        btnDmToggle.setOnClickListener(v -> {
            danmakuOn = !danmakuOn;
            api.setPrefsBool("danmaku_enabled", danmakuOn);
            danmakuView.setEnabled(danmakuOn);
            updateDmBtn();
        });
        btnQuality.setOnClickListener(v -> showQualityDialog());

        findViewById(R.id.btn_play).setOnClickListener(v -> playBuiltin());
        Button ext = findViewById(R.id.btn_play_ext);
        if (ext != null) ext.setOnClickListener(v -> playExternal(0));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        videoView.setOnPreparedListener(mp -> {
            danmakuView.setEnabled(danmakuOn);
            int limitLevel = api.prefsInt("dm_limit_level", 1);
            danmakuView.setSpeedFactor(limitLevel == 0 ? 1.6f : limitLevel == 2 ? 0.9f : 1.2f);
            startTick();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "播放失败(" + what + "/" + extra + ")，可尝试切换画质或外部播放",
                    Toast.LENGTH_LONG).show();
            return true;
        });

        if (!TextUtils.isEmpty(bvid)) loadDetail();
    }

    private void updateDmBtn() {
        btnDmToggle.setText(danmakuOn ? R.string.danmaku_on : R.string.danmaku_off);
    }

    private void loadDetail() {
        BiliApi.get(this).videoDetail(bvid, (data, err) -> {
            if (err != null || data == null) return;
            try {
                JSONObject d = data.optJSONObject("data");
                if (d == null) return;
                if (aid == 0) aid = d.optLong("aid", 0);
                pages = d.optJSONArray("pages");
                LinearLayout list = findViewById(R.id.part_list);
                if (pages == null || pages.length() <= 1) {
                    list.setVisibility(View.GONE);
                    return;
                }
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject p = pages.getJSONObject(i);
                    TextView tv = new TextView(this);
                    tv.setText((i + 1) + ". " + p.optString("part", "P" + (i + 1))
                            + "  (" + VideoItem.fmtDuration(p.optLong("duration", 0)) + ")");
                    tv.setTextColor(0xFFEEEEEE);
                    tv.setTextSize(14);
                    tv.setPadding(16, 20, 16, 20);
                    tv.setBackgroundResource(R.drawable.bg_card);
                    tv.setFocusable(true);
                    final int idx = i;
                    tv.setOnClickListener(v -> playBuiltinPart(idx));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = 8;
                    list.addView(tv, lp);
                }
            } catch (Exception ignored) { }
        });
    }

    private void playBuiltin() { playBuiltinPart(0); }

    /** 内置播放：取 durl 直链 → VideoView（系统播放器自动在内部存储建立缓冲）+ 弹幕时间轴 */
    private void playBuiltinPart(int partIndex) {
        playerBox.setVisibility(View.VISIBLE);
        BiliApi.get(this).playUrls(bvid, aid, (data, err) -> {
            if (err != null || data == null) {
                Toast.makeText(this, "获取播放地址失败: " + err, Toast.LENGTH_LONG).show();
                return;
            }
            try {
                JSONArray durl = data.optJSONArray("durl");
                if (durl == null || durl.length() == 0) {
                    Toast.makeText(this, "该清晰度不可用，请切换画质", Toast.LENGTH_SHORT).show();
                    return;
                }
                int idx = Math.min(partIndex, durl.length() - 1);
                String url = durl.getJSONObject(idx).optString("url");
                if (TextUtils.isEmpty(url)) return;
                loadDanmaku();
                videoView.setVideoURI(Uri.parse(url));
                videoView.start();
                videoView.requestFocus();
            } catch (Exception e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadDanmaku() {
        BiliApi.get(this).danmaku(aid, (dd, de) -> {
            if (de != null || dd == null) return;
            List<DanmakuView.DM> list = new ArrayList<>();
            JSONArray arr = dd.optJSONArray("list");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                try { list.add(danmakuView.parse(arr.getJSONObject(i))); } catch (Exception ignored) { }
            }
            runOnUiThread(() -> danmakuView.setData(list));
        });
    }

    private void startTick() {
        if (tick != null) handler.removeCallbacks(tick);
        tick = new Runnable() {
            @Override public void run() {
                try {
                    if (videoView.isPlaying()) {
                        danmakuView.seekTo(videoView.getCurrentPosition() / 1000.0);
                    }
                } catch (Exception ignored) { }
                handler.postDelayed(this, 250);
            }
        };
        handler.post(tick);
    }

    /** 画质切换对话框 */
    private void showQualityDialog() {
        final int[] qns = {16, 32, 64, 80, 112};
        final String[] names = {"360P", "720P", "1080P", "1080P 高码率", "4K（需支持）"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.setting_quality)
                .setItems(names, (d, w) -> {
                    BiliApi.get(this).setQualityQn(qns[w]);
                    Toast.makeText(this, "已切换为 " + names[w] + "，重新播放生效", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ---------------- 遥控器按键适配 ----------------
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean playing = playerBox.getVisibility() == View.VISIBLE;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (playing) {
                    if (videoView.isPlaying()) videoView.pause(); else videoView.start();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (playing && event.getRepeatCount() == 0) { seekBy(-10000); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (playing && event.getRepeatCount() == 0) { seekBy(10000); return true; }
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (playing) { seekBy(-30000); return true; }
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (playing) { seekBy(30000); return true; }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (playing) { videoView.start(); return true; }
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (playing) { videoView.pause(); return true; }
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (playing) { closePlayer(); return true; }
                break;
            case KeyEvent.KEYCODE_MENU:
                showQualityDialog(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void seekBy(int ms) {
        try {
            int pos = Math.max(0, videoView.getCurrentPosition() + ms);
            videoView.seekTo(pos);
            danmakuView.seekTo(pos / 1000.0);
            Toast.makeText(this, "跳转到 " + VideoItem.fmtDuration(pos / 1000), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    private void closePlayer() {
        try { if (videoView.isPlaying()) videoView.pause(); videoView.stopPlayback(); } catch (Exception ignored) { }
        playerBox.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { if (videoView.isPlaying()) videoView.pause(); } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try { videoView.suspend(); } catch (Exception ignored) { }
    }

    @Override
    public void onBackPressed() {
        if (playerBox.getVisibility() == View.VISIBLE) { closePlayer(); return; }
        super.onBackPressed();
    }

    /** 外部播放 / 浏览器打开 */
    private void playExternal(int partIndex) {
        String webUrl = TextUtils.isEmpty(bvid)
                ? ("https://www.bilibili.com/video/av" + aid)
                : ("https://www.bilibili.com/video/" + bvid
                + (partIndex > 0 ? ("?p=" + (partIndex + 1)) : ""));
        BiliApi.get(this).playUrls(bvid, aid, (data, err) -> {
            String direct = null;
            if (err == null && data != null) {
                try {
                    JSONArray durl = data.optJSONArray("durl");
                    if (durl != null && durl.length() > 0) direct = durl.getJSONObject(0).optString("url");
                } catch (Exception ignored) { }
            }
            String target = (direct != null && !direct.isEmpty()) ? direct : webUrl;
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(Uri.parse(target), "video/*");
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(it);
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
                } catch (ActivityNotFoundException e2) {
                    Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
