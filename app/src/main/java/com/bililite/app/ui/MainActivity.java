package com.bililite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;
import com.bililite.app.api.VideoItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面：热门 / 排行 / 收藏 / 历史 四个内容 Tab + “我的”入口。
 * TV 遥控器方向键可自然导航（RecyclerView + Focusable item）。
 */
public class MainActivity extends AppCompatActivity {

    private static final int TAB_POPULAR = 0, TAB_RANKING = 1, TAB_FAV = 2, TAB_HIST = 3, TAB_MINE = 4;

    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;
    private RecyclerView recycler;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private TextView tvEmpty;
    private int currentTab = TAB_POPULAR;
    private int page = 1;
    private boolean loadingMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recycler = findViewById(R.id.recycler);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new VideoAdapter(this, items, this::openVideo);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(() -> loadFirstPage());
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loadingMore) return;
                LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
                if (llm != null && llm.findLastVisibleItemPosition() >= items.size() - 3
                        && currentTab == TAB_POPULAR) {
                    loadNextPage();
                }
            }
        });

        ((TextView) findViewById(R.id.tab_popular)).setOnClickListener(v -> switchTab(TAB_POPULAR));
        ((TextView) findViewById(R.id.tab_ranking)).setOnClickListener(v -> switchTab(TAB_RANKING));
        ((TextView) findViewById(R.id.tab_favorite)).setOnClickListener(v -> switchTab(TAB_FAV));
        ((TextView) findViewById(R.id.tab_history)).setOnClickListener(v -> switchTab(TAB_HIST));
        ((TextView) findViewById(R.id.tab_mine)).setOnClickListener(v -> switchTab(TAB_MINE));

        findViewById(R.id.btn_search).setOnClickListener(
                v -> startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.btn_login_top).setOnClickListener(v -> {
            if (BiliApi.get(this).isLogin()) {
                BiliApi.get(this).logout();
                Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
                refreshLoginUi();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });

        refreshLoginUi();
        switchTab(TAB_POPULAR);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLoginUi();
    }

    private void refreshLoginUi() {
        BiliApi api = BiliApi.get(this);
        TextView btn = findViewById(R.id.btn_login_top);
        btn.setText(api.isLogin() ? (TextUtils.isEmpty(api.uname()) ? "已登录" : api.uname())
                : getString(R.string.login));
    }

    private void highlightTab(int tab) {
        int[] ids = {R.id.tab_popular, R.id.tab_ranking, R.id.tab_favorite, R.id.tab_history, R.id.tab_mine};
        for (int i = 0; i < ids.length; i++) {
            TextView tv = findViewById(ids[i]);
            tv.setTextColor(i == tab ? 0xFFFb7299 /* pink */ : 0xFF888888);
            tv.getPaint().setFakeBoldText(i == tab);
        }
    }

    private void switchTab(int tab) {
        currentTab = tab;
        page = 1;
        highlightTab(tab);
        if (tab == TAB_MINE) {
            // “我的”直接复用收藏视图；未登录则跳登录页
            if (!BiliApi.get(this).isLogin()) {
                startActivity(new Intent(this, LoginActivity.class));
                highlightTab(currentTab == TAB_MINE ? TAB_POPULAR : currentTab);
                currentTab = TAB_POPULAR;
                loadFirstPage();
                return;
            }
            loadFirstPage();
            return;
        }
        loadFirstPage();
    }

    private void loadFirstPage() {
        items.clear();
        adapter.notifyDataSetChanged();
        page = 1;
        setLoading(true);
        fetchPage(true);
    }

    private void loadNextPage() {
        loadingMore = true;
        page++;
        fetchPage(false);
    }

    private void fetchPage(boolean first) {
        BiliApi.CB cb = (data, err) -> {
            setLoading(false);
            swipe.setRefreshing(false);
            loadingMore = false;
            if (err != null) {
                if (first) items.clear();
                adapter.notifyDataSetChanged();
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (!items.isEmpty()) tvEmpty.setText(R.string.load_failed);
                return;
            }
            try {
                JSONArray arr = data.getJSONArray("list");
                List<VideoItem> got = VideoItem.listFrom(arr);
                if (got.isEmpty() && first) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
                items.addAll(got);
                adapter.notifyDataSetChanged();
            } catch (Exception e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        };
        BiliApi api = BiliApi.get(this);
        switch (currentTab) {
            case TAB_RANKING:
                api.ranking("all", cb);
                break;
            case TAB_FAV:
            case TAB_MINE:
                if (!api.isLogin()) {
                    setLoading(false);
                    swipe.setRefreshing(false);
                    tvEmpty.setText(getString(R.string.not_login));
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                api.favFolderList(cb);
                break;
            case TAB_HIST:
                if (!api.isLogin()) {
                    setLoading(false);
                    swipe.setRefreshing(false);
                    tvEmpty.setText(getString(R.string.not_login));
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                api.history(cb);
                break;
            default:
                api.popular(page, cb);
        }
    }

    private void setLoading(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void openVideo(VideoItem it) {
        if (it.mid > 0 && currentTab == TAB_FAV) {
            // 收藏文件夹点击 -> 暂以详情打开
        }
        Intent in = new Intent(this, PlayActivity.class);
        in.putExtra("title", it.title);
        in.putExtra("bvid", it.bvid);
        in.putExtra("aid", it.aid);
        in.putExtra("cover", it.url());
        in.putExtra("uploader", it.uploader);
        in.putExtra("mid", it.mid);
        in.putExtra("plays", it.plays);
        in.putExtra("danmaku", it.danmaku);
        in.putExtra("date", it.dateText);
        in.putExtra("intro", it.intro);
        startActivity(in);
    }
}
