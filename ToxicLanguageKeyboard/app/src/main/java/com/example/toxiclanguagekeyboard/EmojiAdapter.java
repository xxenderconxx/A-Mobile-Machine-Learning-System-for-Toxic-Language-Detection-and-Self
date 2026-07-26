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
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        // Custom styling for emoji display
        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setTextSize(24);
        return new EmojiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
        String emoji = emojis.get(position);
        ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(emoji);
        holder.itemView.setOnClickListener(v -> listener.onEmojiClick(emoji));
    }

    @Override
    public int getItemCount() {
        return emojis.size();
    }

    static class EmojiViewHolder extends RecyclerView.ViewHolder {
        EmojiViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
