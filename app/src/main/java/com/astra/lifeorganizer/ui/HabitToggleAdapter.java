package com.astra.lifeorganizer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HabitToggleAdapter extends RecyclerView.Adapter<HabitToggleAdapter.ToggleViewHolder> {

    private List<Item> habits = new ArrayList<>();
    private Set<Long> completedIds = new HashSet<>();

    public void setHabits(List<Item> habits) {
        this.habits = habits;
        notifyDataSetChanged();
    }

    public List<Item> getHabits() {
        return habits;
    }

    public Set<Long> getCompletedIds() {
        return completedIds;
    }

    @NonNull
    @Override
    public ToggleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_habit_toggle, parent, false);
        return new ToggleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ToggleViewHolder holder, int position) {
        Item habit = habits.get(position);
        holder.tvTitle.setText(habit.title);
        
        holder.cbCompleted.setOnCheckedChangeListener(null);
        holder.cbCompleted.setChecked(completedIds.contains(habit.id));
        
        holder.cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                completedIds.add(habit.id);
            } else {
                completedIds.remove(habit.id);
            }
        });

        holder.itemView.setOnClickListener(v -> holder.cbCompleted.toggle());
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    static class ToggleViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        CheckBox cbCompleted;

        public ToggleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_habit_title);
            cbCompleted = itemView.findViewById(R.id.cb_completed);
        }
    }
}
