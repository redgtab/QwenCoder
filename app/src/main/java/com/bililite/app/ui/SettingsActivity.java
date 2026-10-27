package com.bililite.app.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bililite.app.BuildConfig;
import com.bililite.app.R;
import com.bililite.app.api.BiliApi;

import org.json.JSONObject;

/** 设置页：画质 / 弹幕开关 / 自动内置播放 / 检查更新 / 清除缓存 / 关于 */
public class SettingsActivity extends AppCompatActivity {

    private static final int[] QN = {16, 32, 64, 80, 112};
    private static final String[] QN_NAME = {"480P", "720P", "1080P", "1080P高码率(大会员)", "4K(大会员)"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        bind(R.id.row_quality, v -> {
            BiliApi api = BiliApi.get(this);
            int cur = api.qualityQn();
            String[] names = new String[QN.length];
            for (int i = 0; i < QN.length; i++)
                names[i] = QN_NAME[i] + (QN[i] == cur ? "（当前）" : "");
            new AlertDialog.Builder(this).setTitle("默认画质")
                    .setItems(names, (d, w) -> {
                        api.setQualityQn(QN[w]);
                        ((TextView) findViewById(R.id.val_quality)).setText(QN_NAME[w]);
                        toast("已设为 " + QN_NAME[w]);
                    }).show();
        });

        bind(R.id.row_danmaku, v -> toggleBool("danmaku_enabled", R.id.val_danmaku));
        bind(R.id.row_auto_play, v -> toggleBool("auto_builtin_play", R.id.val_auto_play));
        bind(R.id.row_dm_limit, v -> {
            String[] opts = {"少（省电流畅）", "标准", "多（热闹）"};
            int cur = BiliApi.get(this).prefsInt("dm_limit_level", 1);
            new AlertDialog.Builder(this).setTitle("同屏弹幕数量")
                    .setItems(opts, (d, w) -> {
                        BiliApi.get(this).setPrefsInt("dm_limit_level", w);
                        ((TextView) findViewById(R.id.val_dm_limit)).setText(opts[w]);
                    }).show();
        });

        bind(R.id.row_update, v -> checkUpdate());
        bind(R.id.row_crash, v -> startActivity(new android.content.Intent(
                this, CrashLogActivity.class)));
        ((TextView) findViewById(R.id.val_crash)).setText(
                com.bililite.app.CrashReport.hasNewCrash(this) ? "有崩溃记录!" : "");
        bind(R.id.row_cache, v -> {
            ImageLoader.clearDiskCache();
            toast("图片缓存已清除");
        });
        bind(R.id.row_about, v -> new AlertDialog.Builder(this)
                .setTitle("BiliLite " + BuildConfig.VERSION_NAME)
                .setMessage("轻量 B 站客户端\n开源协议：MIT\n项目：github.com/redgtab/QwenCoder")
                .setPositiveButton("好", null).show());

        // 初始显示值
        BiliApi api = BiliApi.get(this);
        int q = api.qualityQn();
        String qName = "自定义";
        for (int i = 0; i < QN.length; i++) if (QN[i] == q) qName = QN_NAME[i];
        ((TextView) findViewById(R.id.val_quality)).setText(qName);
        ((TextView) findViewById(R.id.val_danmaku)).setText(api.prefsBool("danmaku_enabled", true) ? "开" : "关");
        ((TextView) findViewById(R.id.val_auto_play)).setText(api.prefsBool("auto_builtin_play", false) ? "开" : "关");
        ((TextView) findViewById(R.id.val_dm_limit)).setText(new String[]{"少（省电流畅）", "标准", "多（热闹）"}[api.prefsInt("dm_limit_level", 1)]);
        ((TextView) findViewById(R.id.val_version)).setText(BuildConfig.VERSION_NAME);
    }

    private void bind(int id, android.view.View.OnClickListener l) {
        findViewById(id).setOnClickListener(l);
    }

    private void toggleBool(String key, int valId) {
        BiliApi api = BiliApi.get(this);
        boolean nv = !api.prefsBool(key, true);
        api.setPrefsBool(key, nv);
        ((TextView) findViewById(valId)).setText(nv ? "开" : "关");
    }

    private void checkUpdate() {
        toast("正在检查更新…");
        BiliApi.get(this).checkUpdate((data, err) -> {
            if (err != null) { postToast("检查失败：" + err); return; }
            try {
                String tag = data.getString("tag").replace("v", "").trim();
                String cur = BuildConfig.VERSION_NAME;
                boolean newer = isNewer(tag, cur);
                runOnUiThread(() -> {
                    String body = "最新版本: " + data.optString("name", tag) + "\n"
                            + data.optString("body", "");
                    if (newer) {
                        String apk = data.optString("apk", "");
                        new AlertDialog.Builder(this)
                                .setTitle("发现新版本 " + tag)
                                .setMessage(body)
                                .setPositiveButton(apk.isEmpty() ? "去 Releases 页面" : "下载 APK", (d, w) -> {
                                    String url = apk.isEmpty()
                                            ? "https://github.com/redgtab/QwenCoder/releases/latest" : apk;
                                    startActivity(new android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)));
                                })
                                .setNegativeButton("取消", null).show();
                    } else {
                        toast("已是最新版本 (" + cur + ")");
                    }
                });
            } catch (Exception e) { postToast("解析失败: " + e.getMessage()); }
        });
    }

    /** 版本号比较：1.2.3 > 1.2.0 */
    public static boolean isNewer(String latest, String current) {
        String[] a = latest.split("\\."), b = current.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? num(a[i]) : 0, y = i < b.length ? num(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }
    private static int num(String s) {
        StringBuilder d = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isDigit(c)) d.append(c); else break;
        try { return d.length() == 0 ? 0 : Integer.parseInt(d.toString()); } catch (Exception e) { return 0; }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void postToast(String s) { runOnUiThread(() -> toast(s)); }
}
