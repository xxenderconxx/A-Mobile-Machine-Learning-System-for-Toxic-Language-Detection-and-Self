package com.example.toxiclanguagekeyboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {

    public interface OnEmojiClickListener {
        void onEmojiClick(String emoji);
    }

    private List<String> emojis;
    private final OnEmojiClickListener listener;

    public EmojiAdapter(List<String> emojis, OnEmojiClickListener listener) {
        this.emojis = emojis;
        this.listener = listener;
    }

    public void updateData(List<String> newEmojis) {
        this.emojis = new ArrayList<>(newEmojis);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emoji, parent, false);
        return new EmojiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
        String emoji = emojis.get(position);
        holder.emojiText.setText(emoji);
        holder.itemView.setOnClickListener(v -> listener.onEmojiClick(emoji));
    }

    @Override
    public int getItemCount() {
        return emojis.size();
    }

    static class EmojiViewHolder extends RecyclerView.ViewHolder {
        TextView emojiText;

        EmojiViewHolder(@NonNull View itemView) {
            super(itemView);
            emojiText = itemView.findViewById(R.id.emoji_text);
        }
    }
}
