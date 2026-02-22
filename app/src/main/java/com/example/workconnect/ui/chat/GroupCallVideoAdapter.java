package com.example.workconnect.ui.chat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GroupCallVideoAdapter extends RecyclerView.Adapter<GroupCallVideoAdapter.VideoViewHolder> {

    // Payload tags for partial updates 
    static final String PAYLOAD_AUDIO = "audio";
    static final String PAYLOAD_VIDEO = "video";

    // Data 
    private final List<VideoItem> videoItems = new ArrayList<>();
    private final Map<Integer, String> uidToName = new HashMap<>();

    // SurfaceView cache: uid → SurfaceView.  Never recreated once created.
    private final Map<Integer, android.view.SurfaceView> surfaceViewCache = new HashMap<>();

    // Listeners 
    public interface OnVideoSetupListener {
        void onVideoSetup(int uid, android.view.SurfaceView surfaceView);
    }

    public interface OnThumbnailClickListener {
        void onThumbnailClick(int uid);
    }

    private OnVideoSetupListener onVideoSetupListener;
    private OnThumbnailClickListener onThumbnailClickListener;

    public void setOnVideoSetupListener(OnVideoSetupListener listener) {
        this.onVideoSetupListener = listener;
    }

    public void setOnThumbnailClickListener(OnThumbnailClickListener listener) {
        this.onThumbnailClickListener = listener;
    }

    // Model 
    public static class VideoItem {
        public int uid;
        public boolean isLocal;
        public boolean hasVideo;
        public boolean hasAudio;

        public VideoItem(int uid, boolean isLocal, boolean hasVideo) {
            this.uid = uid;
            this.isLocal = isLocal;
            this.hasVideo = hasVideo;
            this.hasAudio = true;
        }
    }

    // ViewHolder 
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;
        // Stable sub-views kept across partial rebinds:
        FrameLayout videoSlot;   // hosts the cached SurfaceView or avatar
        ImageView   avatarView;
        TextView    nameText;
        ImageView   audioIndicator;

        public VideoViewHolder(@NonNull FrameLayout root) {
            super(root);
            container = root;
            buildLayout(root.getContext());
        }

        private void buildLayout(android.content.Context ctx) {
            float dp = ctx.getResources().getDisplayMetrics().density;

            // Slot that holds either a SurfaceView or an avatar icon
            videoSlot = new FrameLayout(ctx);
            videoSlot.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
            videoSlot.setBackgroundColor(0xFF1A1A1A);
            container.addView(videoSlot);

            // Bottom info bar
            FrameLayout infoBar = new FrameLayout(ctx);
            infoBar.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM));
            infoBar.setBackgroundColor(0x80000000);
            container.addView(infoBar);

            nameText = new TextView(ctx);
            nameText.setTextColor(0xFFFFFFFF);
            nameText.setTextSize(11);
            nameText.setPadding((int)(4*dp), (int)(2*dp), (int)(4*dp), (int)(2*dp));
            nameText.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL));
            infoBar.addView(nameText);

            audioIndicator = new ImageView(ctx);
            int iconSize = (int)(18 * dp);
            FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(iconSize, iconSize,
                android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            ap.setMargins(0, 0, (int)(4*dp), 0);
            audioIndicator.setLayoutParams(ap);
            infoBar.addView(audioIndicator);
        }
    }

    // RecyclerView overrides 

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        float density = parent.getContext().getResources().getDisplayMetrics().density;
        int widthPx = (int)(80 * density);

        FrameLayout container = new FrameLayout(parent.getContext());
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
            widthPx, RecyclerView.LayoutParams.MATCH_PARENT);
        params.setMargins((int)(4*density), (int)(4*density), (int)(4*density), (int)(4*density));
        container.setLayoutParams(params);
        container.setBackgroundColor(0xFF1A1A1A);

        return new VideoViewHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position,
                                 @NonNull java.util.List<Object> payloads) {
        if (payloads.isEmpty()) {
            // Full bind
            onBindViewHolder(holder, position);
            return;
        }
        // Partial bind: only update what changed 
        VideoItem item = videoItems.get(position);
        for (Object p : payloads) {
            if (PAYLOAD_AUDIO.equals(p)) {
                bindAudioIndicator(holder, item);
            } else if (PAYLOAD_VIDEO.equals(p)) {
                bindVideoSlot(holder, item);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem item = videoItems.get(position);

        // Video slot: attach cached SurfaceView 
        bindVideoSlot(holder, item);

        // Name 
        String name = uidToName.get(item.uid);
        if (name == null || name.trim().isEmpty()) {
            name = item.isLocal ? "You" : "Participant";
        }
        holder.nameText.setText(name);

        // Audio indicator 
        bindAudioIndicator(holder, item);

        // Click: any thumbnail (including local) → promote to main view 
        final int clickUid = item.uid;
        holder.container.setOnClickListener(v -> {
            if (onThumbnailClickListener != null) {
                onThumbnailClickListener.onThumbnailClick(clickUid);
            }
        });
    }

    // Attach the cached SurfaceView (or an avatar) into the video slot.
    private void bindVideoSlot(@NonNull VideoViewHolder holder, VideoItem item) {
        holder.videoSlot.removeAllViews();

        if (item.hasVideo) {
            android.view.SurfaceView sv = getOrCreateSurfaceView(item.uid, item.isLocal,
                holder.videoSlot.getContext());
            // Detach from any previous parent before adding here
            if (sv.getParent() != null) {
                ((ViewGroup) sv.getParent()).removeView(sv);
            }
            sv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
            holder.videoSlot.addView(sv);
        } else {
            // No video: show avatar icon
            ImageView avatar = new ImageView(holder.videoSlot.getContext());
            avatar.setImageResource(R.drawable.ic_avatar);
            avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            avatar.setBackgroundColor(0xFF1A1A1A);
            avatar.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
            holder.videoSlot.addView(avatar);
        }
    }

    private void bindAudioIndicator(@NonNull VideoViewHolder holder, VideoItem item) {
        if (item.isLocal) {
            holder.audioIndicator.setVisibility(View.GONE);
        } else {
            holder.audioIndicator.setVisibility(View.VISIBLE);
            holder.audioIndicator.setImageResource(
                item.hasAudio ? R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        }
    }

    /**
     * Returns an existing cached SurfaceView for this UID, or creates one and
     * calls onVideoSetupListener exactly ONCE so Agora sets up its render target.
     */
    private android.view.SurfaceView getOrCreateSurfaceView(int uid, boolean isLocal,
                                                             android.content.Context ctx) {
        if (surfaceViewCache.containsKey(uid)) {
            return surfaceViewCache.get(uid);
        }
        android.view.SurfaceView sv = new android.view.SurfaceView(ctx);
        sv.setZOrderMediaOverlay(false);
        surfaceViewCache.put(uid, sv);
        // Tell Agora about this surface exactly once
        if (onVideoSetupListener != null) {
            onVideoSetupListener.onVideoSetup(uid, sv);
        }
        return sv;
    }

    @Override
    public int getItemCount() {
        return videoItems.size();
    }

    // Public API 

    public void addVideo(int uid, boolean isLocal, boolean hasVideo) {
        for (int i = 0; i < videoItems.size(); i++) {
            if (videoItems.get(i).uid == uid) {
                if (videoItems.get(i).hasVideo != hasVideo) {
                    videoItems.get(i).hasVideo = hasVideo;
                    notifyItemChanged(i, PAYLOAD_VIDEO);
                }
                return;
            }
        }
        VideoItem item = new VideoItem(uid, isLocal, hasVideo);
        if (isLocal) {
            videoItems.add(0, item);
            notifyItemInserted(0);
        } else {
            videoItems.add(item);
            notifyItemInserted(videoItems.size() - 1);
        }
    }

    public void removeVideo(int uid) {
        for (int i = 0; i < videoItems.size(); i++) {
            if (videoItems.get(i).uid == uid) {
                videoItems.remove(i);
                notifyItemRemoved(i);
                surfaceViewCache.remove(uid); // release cached view
                return;
            }
        }
    }

    public void updateVideoState(int uid, boolean hasVideo) {
        for (int i = 0; i < videoItems.size(); i++) {
            if (videoItems.get(i).uid == uid) {
                if (videoItems.get(i).hasVideo != hasVideo) {
                    videoItems.get(i).hasVideo = hasVideo;
                    notifyItemChanged(i, PAYLOAD_VIDEO);
                }
                return;
            }
        }
    }

    public void updateAudioState(int uid, boolean hasAudio) {
        for (int i = 0; i < videoItems.size(); i++) {
            if (videoItems.get(i).uid == uid) {
                if (videoItems.get(i).hasAudio != hasAudio) {
                    videoItems.get(i).hasAudio = hasAudio;
                    notifyItemChanged(i, PAYLOAD_AUDIO);
                }
                return;
            }
        }
    }

    public void updateParticipantName(int uid, String name) {
        uidToName.put(uid, name);
        for (int i = 0; i < videoItems.size(); i++) {
            if (videoItems.get(i).uid == uid) {
                notifyItemChanged(i); // full rebind to update name
                return;
            }
        }
    }

    public void clear() {
        int size = videoItems.size();
        videoItems.clear();
        uidToName.clear();
        surfaceViewCache.clear();
        notifyItemRangeRemoved(0, size);
    }
}
