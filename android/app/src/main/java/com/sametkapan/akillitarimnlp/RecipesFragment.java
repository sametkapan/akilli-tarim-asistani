package com.sametkapan.akillitarimnlp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sametkapan.akillitarimnlp.models.Recipe;
import com.sametkapan.akillitarimnlp.models.RecipeManager;
import com.sametkapan.akillitarimnlp.services.MqttManagerService;

import java.util.List;
import java.util.Locale;

public class RecipesFragment extends Fragment {

    private RecipeAdapter adapter;
    private MqttManagerService mqttService;
    private boolean isBound = false;

    private String activeRecipeName = "";
    private int activeRecipeDay = 1;
    private boolean isAutoActive = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttManagerService.LocalBinder binder = (MqttManagerService.LocalBinder) service;
            mqttService = binder.getService();
            isBound = true;
            
            if (mqttService.getCurrentRecipe() != null) {
                activeRecipeName = mqttService.getCurrentRecipe().name;
                activeRecipeDay = mqttService.getCurrentDay();
                isAutoActive = mqttService.isAutoMode();
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver recipeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("RECIPE_UPDATED".equals(intent.getAction())) {
                activeRecipeName = intent.getStringExtra("recipeName");
                activeRecipeDay = intent.getIntExtra("currentDay", 1);
                isAutoActive = intent.getBooleanExtra("isAuto", false);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recipes, container, false);
        RecyclerView rvRecipes = view.findViewById(R.id.rvRecipes);
        rvRecipes.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new RecipeAdapter(RecipeManager.getRecipes(), new OnRecipeActionListener() {
            @Override
            public void onActivate(Recipe recipe) {
                if (isBound && mqttService != null) {
                    mqttService.setRecipe(recipe, 1, true);
                    Toast.makeText(getContext(), String.format("%s Aktif Edildi!", recipe.name), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onReset() {
                if (isBound && mqttService != null) {
                    mqttService.resetRecipeDay();
                    Toast.makeText(getContext(), "Plan başa sarıldı.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onTogglePause() {
                if (isBound && mqttService != null) {
                    mqttService.setAutoMode(!mqttService.isAutoMode());
                }
            }

            @Override
            public void onSetDay(int newDay) {
                if (isBound && mqttService != null) {
                    mqttService.setRecipeDay(newDay);
                }
            }
        });
        rvRecipes.setAdapter(adapter);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(requireActivity(), MqttManagerService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(recipeReceiver, new IntentFilter("RECIPE_UPDATED"));
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(recipeReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isBound) {
            requireActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }

    private interface OnRecipeActionListener {
        void onActivate(Recipe recipe);
        void onReset();
        void onTogglePause();
        void onSetDay(int newDay);
    }

    private class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.ViewHolder> {
        private final List<Recipe> recipes;
        private final OnRecipeActionListener listener;

        RecipeAdapter(List<Recipe> recipes, OnRecipeActionListener listener) {
            this.recipes = recipes;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Recipe recipe = recipes.get(position);
            holder.tvName.setText(recipe.name);
            holder.tvDetails.setText(String.format(Locale.getDefault(), "%d Günlük Yetiştirme Planı", recipe.totalDays));
            
            boolean isActive = recipe.name.equals(activeRecipeName);
            
            if (isActive) {
                holder.btnActivate.setVisibility(View.GONE);
                holder.layoutActive.setVisibility(View.VISIBLE);
                holder.tvProgress.setText(String.format(Locale.getDefault(), "İlerleme: %d / %d Gün", activeRecipeDay, recipe.totalDays));
                
                holder.btnPauseResume.setImageResource(isAutoActive ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                
                holder.btnReset.setOnClickListener(v -> listener.onReset());
                holder.btnPauseResume.setOnClickListener(v -> listener.onTogglePause());
                
                holder.btnDayMinus.setOnClickListener(v -> {
                    if (activeRecipeDay > 1) {
                        listener.onSetDay(activeRecipeDay - 1);
                    }
                });
                
                holder.btnDayPlus.setOnClickListener(v -> {
                    if (activeRecipeDay < recipe.totalDays) {
                        listener.onSetDay(activeRecipeDay + 1);
                    }
                });
            } else {
                holder.btnActivate.setVisibility(View.VISIBLE);
                holder.layoutActive.setVisibility(View.GONE);
                holder.btnActivate.setOnClickListener(v -> listener.onActivate(recipe));
            }
        }

        @Override
        public int getItemCount() {
            return recipes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails, tvProgress;
            Button btnActivate;
            ImageButton btnReset, btnPauseResume, btnDayMinus, btnDayPlus;
            View layoutActive;

            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tvRecipeName);
                tvDetails = view.findViewById(R.id.tvRecipeDetails);
                tvProgress = view.findViewById(R.id.tvProgress);
                btnActivate = view.findViewById(R.id.btnActivate);
                btnReset = view.findViewById(R.id.btnReset);
                btnPauseResume = view.findViewById(R.id.btnPauseResume);
                layoutActive = view.findViewById(R.id.layoutActiveControls);
                btnDayMinus = view.findViewById(R.id.btnDayMinus);
                btnDayPlus = view.findViewById(R.id.btnDayPlus);
            }
        }
    }
}
