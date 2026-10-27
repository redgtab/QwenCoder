package com.bililite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 动态页：我的关注动态（需登录）/ 输入 UID 查看别人动态 */
public class DynamicActivity extends AppCompatActivity {

    private final List<JSONObject> items = new ArrayList<>();
    private DynamicAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView btnSwitch, btnGo;
    private EditText etMid;
    private boolean mineMode = true;
    private String offset = "";
    private long targetMid;   // 查看他人时的 mid

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnSwitch = findViewById(R.id.btn_switch_mode);
        btnGo = findViewById(R.id.btn_go_mid);
        etMid = findViewById(R.id.et_mid);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DynamicAdapter(this, items, (mid, name) -> {
            Intent sp = new Intent(this, UserSpaceActivity.class);
            sp.putExtra("mid", mid);
            sp.putExtra("uname", name);
            startActivity(sp);
        });
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView r, int dx, int dy) {
                if (!r.canScrollVertically(1) && !swipe.isRefreshing()
                        && !offset.isEmpty()) loadMore();
            }
        });

        swipe = findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::refresh);

        btnSwitch.setOnClickListener(v -> {
            mineMode = !mineMode;
            if (mineMode) {
                btnSwitch.setText("查看他人");
                etMid.setVisibility(View.GONE);
                btnGo.setVisibility(View.GONE);
                refresh();
            } else {
                btnSwitch.setText("返回我的");
                etMid.setVisibility(View.VISIBLE);
                btnGo.setVisibility(View.VISIBLE);
                etMid.requestFocus();
            }
        });
        btnGo.setOnClickListener(v -> {
            String s = etMid.getText().toString().trim();
            if (TextUtils.isEmpty(s)) { toast("请输入 UID"); return; }
            try {
                targetMid = Long.parseLong(s);
                BiliApi.get(this).userInfoCard(targetMid, (d, e) -> {
                    if (e == null && d != null) {
                        runOnUiThread(() -> {
                            ((TextView) findViewById(R.id.tv_title))
                                    .setText(d.optString("name") + " 的动态");
                            refresh();
                        });
                    } else refresh();
                });
            } catch (NumberFormatException e) { toast("UID 格式错误"); }
        });

        targetMid = getIntent().getLongExtra("mid", 0);
        if (targetMid > 0) {
            mineMode = false;
            btnSwitch.setText("返回我的");
            etMid.setVisibility(View.VISIBLE);
            btnGo.setVisibility(View.VISIBLE);
            etMid.setText(String.valueOf(targetMid));
            ((TextView) findViewById(R.id.tv_title)).setText("TA 的动态");
        }
        refresh();
    }

    private void refresh() {
        items.clear(); adapter.notifyDataSetChanged();
        offset = "";
        loadMore();
    }

    private void loadMore() {
        swipe.setRefreshing(true);
        BiliApi api = BiliApi.get(this);
        BiliApi.CB cb = (data, err) -> {
            swipe.setRefreshing(false);
            if (err != null) {
                if (items.isEmpty()) {
                    JSONObject ph = new JSONObject();
                    try {
                        ph.put("__empty__", true);
                        ph.put("author", new JSONObject().put("name",
                                err.contains("登录") ? "请先登录再查看关注动态" : "加载失败: " + err));
                    } catch (Exception ignored) { }
                    items.add(ph);
                    adapter.notifyItemRangeInserted(items.size() - 1, 1);
                } else toast("加载更多失败: " + err);
                return;
            }
            try {
                JSONArray arr = data.optJSONArray("items");
                int start = items.size();
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it != null) items.add(it);
                }
                offset = data.optString("offset", "");
                if (start == 0 && items.isEmpty()) {
                    JSONObject ph = new JSONObject();
                    ph.put("__empty__", true);
                    ph.put("author", new JSONObject().put("name", getString(R.string.dynamic_empty)));
                    items.add(ph);
                }
                adapter.notifyItemRangeInserted(start, items.size() - start);
            } catch (Exception e) { toast("解析失败: " + e.getMessage()); }
        };
        if (mineMode || targetMid == 0) api.dynamicMine(offset, cb);
        else api.dynamicOfUser(targetMid, offset, cb);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
