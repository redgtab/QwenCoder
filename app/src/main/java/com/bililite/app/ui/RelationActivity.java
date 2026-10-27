package com.bililite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 关注 / 粉丝 列表页（自身或他人 uid），支持分页 */
public class RelationActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "mid";
    public static final String EXTRA_FANS = "fans";

    private final List<String[]> users = new ArrayList<>(); // [mid, name, sign]
    private MyAdapter adapter;
    private boolean loading = false;
    private int page = 1;
    private long uid = 0;
    private boolean fans = false;
    private RecyclerView rv;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_space);
        uid = getIntent().getLongExtra(EXTRA_UID, 0);
        fans = getIntent().getBooleanExtra(EXTRA_FANS, false);
        TextView name = findViewById(R.id.tv_uname);
        name.setText(fans ? getString(R.string.follower) : getString(R.string.following));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyAdapter();
        rv.setAdapter(adapter);
        loadMore();
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView r, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) r.getLayoutManager();
                if (lm != null && dy > 0 && lm.findLastVisibleItemPosition() >= users.size() - 3)
                    loadMore();
            }
        });
    }

    private void loadMore() {
        if (loading) return;
        loading = true;
        BiliApi.CB cb = (resp, err) -> {
            loading = false;
            if (isFinishing()) return;
            if (err != null) { Toast.makeText(RelationActivity.this, err, Toast.LENGTH_SHORT).show(); return; }
            JSONArray list = resp.optJSONArray("list");
            int start = users.size();
            try {
                for (int i = 0; list != null && i < list.length(); i++) {
                    JSONObject o = list.getJSONObject(i);
                    users.add(new String[]{
                            String.valueOf(o.optLong("mid")),
                            o.optString("uname", o.optString("name", "-")),
                            o.optString("sign", "")
                    });
                }
            } catch (Exception ignored) { }
            if (list != null && list.length() > 0) {
                adapter.notifyItemRangeInserted(start, list.length());
                page++;
            }
        };
        if (fans) BiliApi.get(this).followersList(uid, page, cb);
        else BiliApi.get(this).followingList(uid, page, cb);
    }

    private class MyAdapter extends RecyclerView.Adapter<MyAdapter.VH> {
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            String[] u = users.get(position);
            h.name.setText(u[1]);
            h.sign.setText(u[2]);
            h.itemView.setOnClickListener(view -> {
                Intent in = new Intent(RelationActivity.this, UserSpaceActivity.class);
                in.putExtra("mid", Long.parseLong(u[0]));
                in.putExtra("uname", u[1]);
                startActivity(in);
            });
            h.itemView.setFocusable(true);
        }

        @Override
        public int getItemCount() { return users.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, sign;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.tv_uname);
                sign = v.findViewById(R.id.tv_usign);
            }
        }
    }
}
