package com.example.workconnect.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying participants in a call
 */
public class ParticipantListAdapter extends RecyclerView.Adapter<ParticipantListAdapter.ParticipantViewHolder> {

    private final List<ParticipantItem> participants = new ArrayList<>();

    public static class ParticipantItem {
        public final int uid;
        public final String name;
        public boolean hasVideo;
        public boolean hasAudio;
        public boolean isSpeaking;

        public ParticipantItem(int uid, String name, boolean hasVideo, boolean hasAudio) {
            this.uid = uid;
            this.name = name;
            this.hasVideo = hasVideo;
            this.hasAudio = hasAudio;
            this.isSpeaking = false;
        }
    }

    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_participant, parent, false);
        return new ParticipantViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        ParticipantItem item = participants.get(position);
        
        holder.tvName.setText(item.name);
        
        // Video indicator
        holder.ivVideo.setImageResource(item.hasVideo ? 
            R.drawable.ic_camera_on : R.drawable.ic_camera_off);
        holder.ivVideo.setAlpha(item.hasVideo ? 1.0f : 0.5f);
        
        // Audio indicator
        holder.ivAudio.setImageResource(item.hasAudio ? 
            R.drawable.ic_mic_on : R.drawable.ic_mic_off);
        holder.ivAudio.setAlpha(item.hasAudio ? 1.0f : 0.5f);
        
        // Speaking indicator
        holder.viewSpeaking.setVisibility(item.isSpeaking ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    public void setParticipants(List<ParticipantItem> newParticipants) {
        participants.clear();
        participants.addAll(newParticipants);
        notifyDataSetChanged();
    }

    public void updateParticipantVideo(int uid, boolean hasVideo) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).uid == uid) {
                participants.get(i).hasVideo = hasVideo;
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateParticipantAudio(int uid, boolean hasAudio) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).uid == uid) {
                participants.get(i).hasAudio = hasAudio;
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateParticipantSpeaking(int uid, boolean isSpeaking) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).uid == uid) {
                participants.get(i).isSpeaking = isSpeaking;
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void clearAllSpeaking() {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).isSpeaking) {
                participants.get(i).isSpeaking = false;
                notifyItemChanged(i);
            }
        }
    }

    static class ParticipantViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final ImageView ivVideo;
        final ImageView ivAudio;
        final View viewSpeaking;

        ParticipantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_participant_name);
            ivVideo = itemView.findViewById(R.id.iv_video_indicator);
            ivAudio = itemView.findViewById(R.id.iv_audio_indicator);
            viewSpeaking = itemView.findViewById(R.id.view_speaking_indicator);
        }
    }
}
