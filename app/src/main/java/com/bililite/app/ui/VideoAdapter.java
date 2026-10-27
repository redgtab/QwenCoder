package com.bililite.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bililite.app.R;
import com.bililite.app.api.VideoItem;

import java.util.List;

/** 视频列表适配器：手机为卡片样式；TV 上启用焦点缩放效果 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface OnItemClick { void onClick(VideoItem item); }

    private final List<VideoItem> items;
    private final OnItemClick click;
    private final boolean tvMode;

    public VideoAdapter(Context ctx, List<VideoItem> items, OnItemClick click) {
        this.items = items;
        this.click = click;
        this.tvMode = (ctx.getPackageManager().hasSystemFeature("android.software.leanback"));
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        if (tvMode) {
            v.setBackgroundResource(R.drawable.bg_tv_focus);
            v.setFocusable(true);
        }
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH h, int pos) {
        final VideoItem it = items.get(pos);
        h.title.setText(it.title);
        h.uploader.setText(TextUtils.isEmpty(it.uploader) ? "" : ("UP: " + it.uploader));
        h.info.setText((it.plays == null ? "" : it.plays) + " 播放 · " +
                (it.danmaku == null ? "" : it.danmaku) + " 弹幕");
        h.date.setText(it.dateText);
        h.duration.setText(it.duration);
        ImageLoader.load(h.cover, it.url());
        h.itemView.setOnClickListener(v -> { if (click != null) click.onClick(it); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, uploader, info, date, duration;

        VH(View v) {
            super(v);
            cover = v.findViewById(R.id.iv_cover);
            title = v.findViewById(R.id.tv_title);
            uploader = v.findViewById(R.id.tv_uploader);
            info = v.findViewById(R.id.tv_info);
            date = v.findViewById(R.id.tv_date);
            duration = v.findViewById(R.id.tv_duration);
        }
    }
}
