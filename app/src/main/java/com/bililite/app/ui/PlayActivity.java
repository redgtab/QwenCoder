package com.bililite.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;
import com.bililite.app.api.VideoItem;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 视频详情页：展示标题/UP主/简介/分P列表。
 * 播放策略（与 blbl/bbll 思路一致的简化版）：
 *  - TV 设备：跳转系统 Leanback DetailsActivity 风格的详情页，交给外部播放器；
 *  - 手机：优先尝试用第三方播放器打开直链（durl），失败则回退到网页链接。
 */
public class PlayActivity extends AppCompatActivity {

    private String bvid, title, cover, uploader, plays, danmaku, date, intro;
    private long aid, mid;
    private JSONArray pages; // 分 P
    private Button btnPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        Intent in = getIntent();
        bvid = in.getStringExtra("bvid");
        aid = in.getLongExtra("aid", 0);
        title = in.getStringExtra("title");
        cover = in.getStringExtra("cover");
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

        btnPlay = findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(v -> play(0));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (!TextUtils.isEmpty(bvid)) loadDetail();
    }

    private void loadDetail() {
        BiliApi.get(this).videoDetail(bvid, (data, err) -> {
            if (err != null || data == null) return;
            try {
                JSONObject d = data.optJSONObject("data");
                if (d == null) return;
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
                    tv.setTextColor(0xFF222222);
                    tv.setTextSize(14);
                    tv.setPadding(16, 20, 16, 20);
                    tv.setBackgroundResource(R.drawable.bg_card);
                    final int idx = i;
                    tv.setOnClickListener(v -> play(idx));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = 8;
                    list.addView(tv, lp);
                }
            } catch (Exception ignored) { }
        });
    }

    /** 交给外部播放器 / 浏览器；TV 上走系统详情转发流程 */
    private void play(int partIndex) {
        String webUrl = TextUtils.isEmpty(bvid)
                ? ("https://www.bilibili.com/video/av" + aid)
                : ("https://www.bilibili.com/video/" + bvid
                + (partIndex > 0 ? ("?p=" + (partIndex + 1)) : ""));

        boolean isTv = getPackageManager().hasSystemFeature("android.software.leanback");
        if (isTv) {
            Intent tvIntent = new Intent("com.bililite.app.VIDEO_DETAILS");
            tvIntent.setPackage(getPackageName());
            tvIntent.putExtra("web_url", webUrl);
            tvIntent.putExtra("title", title);
            try {
                startActivity(tvIntent);
                return;
            } catch (ActivityNotFoundException ignored) { }
        }
        // 手机：尝试唤起常见第三方播放器打开网页链接（部分播放器支持解析 B 站页面）
        String[] players = {"tv.danmaku.bili", "com.github.hziapp.bilibili", "moe.fuqiuluo.unofficial"};
        for (String pkg : players) {
            try {
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                it.setPackage(pkg);
                startActivity(it);
                return;
            } catch (Exception ignored) { }
        }
        // 最终回退：任意可处理 http 的应用（浏览器等）
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show();
        }
    }
}
