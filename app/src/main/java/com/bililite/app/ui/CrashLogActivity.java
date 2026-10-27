package com.bililite.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bililite.app.CrashReport;
import com.bililite.app.R;

/** 崩溃日志查看页：展示上次崩溃堆栈，可提交 GitHub Issue / 复制 / 删除 */
public class CrashLogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_log);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView tv = findViewById(R.id.tv_crash_log);
        String log = CrashReport.readLast(this);
        if (TextUtils.isEmpty(log)) {
            tv.setText("暂无崩溃记录。应用运行良好 ✓");
            findViewById(R.id.btn_submit).setEnabled(false);
        } else {
            tv.setText(log);
        }

        findViewById(R.id.btn_submit).setOnClickListener(v -> {
            if (TextUtils.isEmpty(CrashReport.readLast(this))) {
                Toast.makeText(this, "没有可提交的日志", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("提交到 GitHub")
                    .setMessage("崩溃日志已复制到剪贴板。\n即将打开 GitHub 新建 Issue 页面（需浏览器/登录）。\n在正文中长按粘贴即可。")
                    .setPositiveButton("打开 Issue 页", (d, w) -> CrashReport.openGithubIssue(this))
                    .setNegativeButton("取消", null).show();
        });

        findViewById(R.id.btn_copy).setOnClickListener(v -> {
            CrashReport.copyToClipboard(this, "crash", CrashReport.readLast(this));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            CrashReport.clear(this);
            ((TextView) findViewById(R.id.tv_crash_log)).setText("暂无崩溃记录。应用运行良好 ✓");
            findViewById(R.id.btn_submit).setEnabled(false);
            Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show();
        });
    }
}
