package com.sametkapan.akillitarimnlp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.sametkapan.akillitarimnlp.services.MqttManagerService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // MQTT Servisini Başlat
        Intent serviceIntent = new Intent(this, MqttManagerService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        
        // Başlangıç fragment'ı
        loadFragment(new DashboardFragment());

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_dashboard) {
                selectedFragment = new DashboardFragment();
            } else if (itemId == R.id.nav_recipes) {
                selectedFragment = new RecipesFragment();
            } else if (itemId == R.id.nav_analysis) {
                selectedFragment = new AnalysisFragment();
            } else if (itemId == R.id.nav_assistant) {
                selectedFragment = new AssistantFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}