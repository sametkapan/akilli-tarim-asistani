package com.sametkapan.akillitarimnlp;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.sametkapan.akillitarimnlp.models.Recipe;
import com.sametkapan.akillitarimnlp.services.MqttManagerService;

import org.json.JSONObject;

import java.util.Locale;

public class DashboardFragment extends Fragment implements SensorEventListener {

    private TextView tvVpdValue, tvVpdStatus, tvVpdAdvice, tvTemp, tvHum, tvSoil, tvActiveRecipeName;
    private IvyProgressView ivyProgress;
    private MaterialSwitch switchPump;
    private MaterialCardView cardTemp;
    
    private SensorManager sensorManager;
    private Sensor gravitySensor;
    private LiquidWaveView liquidWaveView;
    
    private ArgbEvaluator argbEvaluator = new ArgbEvaluator();
    private int currentTempColor = Color.WHITE;

    private MqttManagerService mqttService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttManagerService.LocalBinder binder = (MqttManagerService.LocalBinder) service;
            mqttService = binder.getService();
            isBound = true;
            updateRecipeUI();
            updateUIFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver mqttReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("MQTT_DATA".equals(action)) {
                String topic = intent.getStringExtra("topic");
                String payload = intent.getStringExtra("payload");
                if (MqttManagerService.TOPIC_SENSOR.equals(topic)) {
                    updateSensorUI(payload);
                }
            } else if ("RECIPE_UPDATED".equals(action)) {
                updateRecipeUI();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        tvVpdValue = view.findViewById(R.id.tvVpdValue);
        tvVpdStatus = view.findViewById(R.id.tvVpdStatus);
        tvVpdAdvice = view.findViewById(R.id.tvVpdAdvice);
        tvTemp = view.findViewById(R.id.tvTemp);
        tvHum = view.findViewById(R.id.tvHum);
        tvSoil = view.findViewById(R.id.tvSoil);
        tvActiveRecipeName = view.findViewById(R.id.tvActiveRecipeName);
        ivyProgress = view.findViewById(R.id.ivyProgress);
        switchPump = view.findViewById(R.id.switchPump);
        liquidWaveView = view.findViewById(R.id.liquidWaveView);
        cardTemp = view.findViewById(R.id.cardTemp);
        
        currentTempColor = getResources().getColor(R.color.card_bg, null);
        
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravitySensor == null) {
                gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }

        switchPump.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBound && mqttService != null) {
                mqttService.publishMessage(MqttManagerService.TOPIC_PUMP, isChecked ? "1" : "0");
            }
        });

        return view;
    }

    private void updateRecipeUI() {
        if (isBound && mqttService != null) {
            Recipe current = mqttService.getCurrentRecipe();
            boolean isAuto = mqttService.isAutoMode();
            if (current != null && isAuto) {
                tvActiveRecipeName.setText("Aktif Plan: " + current.name);
            } else {
                tvActiveRecipeName.setText("Aktif Plan: Manuel Kontrol");
            }
        }
    }

    private void updateUIFromService() {
        if (isBound && mqttService != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("sicaklik", mqttService.getLastTemp());
                json.put("nem", mqttService.getLastHum());
                
                int simulatedRaw = (int) ((mqttService.getLastMoisture() / 100.0) * 4095.0);
                json.put("toprak1", simulatedRaw);
                json.put("toprak2", simulatedRaw);
                json.put("toprak3", simulatedRaw);
                
                updateSensorUI(json.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSensorUI(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            double temp = json.optDouble("sicaklik", 0.0);
            double hum = json.optDouble("nem", 0.0);
            int s1 = json.optInt("toprak1", 0);
            int s2 = json.optInt("toprak2", 0);
            int s3 = json.optInt("toprak3", 0);
            
            int avgSoilRaw = (s1 + s2 + s3) / 3;
            int soilPercent = (int) ((avgSoilRaw / 4095.0) * 100);

            tvTemp.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
            tvHum.setText(String.format(Locale.getDefault(), "%.0f%%", hum));
            tvSoil.setText(String.format(Locale.getDefault(), "%d%%", soilPercent));
            if (ivyProgress != null) {
                ivyProgress.setProgress(soilPercent);
            }
            
            if (liquidWaveView != null) {
                liquidWaveView.setProgress((float) hum);
            }
            
            // Sıcaklığa göre yumuşak renk değişimi (10C Mavi -> 20C Sarı -> 25C Turuncu -> 30C Kırmızı)
            int colorLightBlue = Color.parseColor("#81D4FA");
            int colorLightYellow = Color.parseColor("#FFF59D");
            int colorLightOrange = Color.parseColor("#FFB74D");
            int colorLightRed = Color.parseColor("#E57373");
            
            int targetColor;
            if (temp <= 10.0) {
                targetColor = colorLightBlue;
            } else if (temp <= 20.0) {
                float fraction = (float) ((temp - 10.0) / 10.0);
                targetColor = (int) argbEvaluator.evaluate(fraction, colorLightBlue, colorLightYellow);
            } else if (temp <= 25.0) {
                float fraction = (float) ((temp - 20.0) / 5.0);
                targetColor = (int) argbEvaluator.evaluate(fraction, colorLightYellow, colorLightOrange);
            } else if (temp <= 30.0) {
                float fraction = (float) ((temp - 25.0) / 5.0);
                targetColor = (int) argbEvaluator.evaluate(fraction, colorLightOrange, colorLightRed);
            } else {
                targetColor = colorLightRed;
            }
            
            if (currentTempColor != targetColor && cardTemp != null) {
                ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), currentTempColor, targetColor);
                colorAnimation.setDuration(800); // 800ms yumuşak geçiş animasyonu
                colorAnimation.addUpdateListener(animator -> {
                    currentTempColor = (int) animator.getAnimatedValue();
                    cardTemp.setCardBackgroundColor(currentTempColor);
                });
                colorAnimation.start();
            }

            double vpd = calculateVPD(temp, hum);
            tvVpdValue.setText(String.format(Locale.getDefault(), "%.2f kPa", vpd));

            updateVpdStatus(vpd);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateVpdStatus(double vpd) {
        double vpdMin = 0.8;
        double vpdMax = 1.2;

        // Aktif plana göre hedef VPD aralığını al
        if (isBound && mqttService != null) {
            Recipe.Phase phase = mqttService.getCurrentPhase();
            if (phase != null) {
                vpdMin = phase.targetVpdMin;
                vpdMax = phase.targetVpdMax;
            }
        }

        if (vpd < vpdMin) {
            tvVpdStatus.setText("DÜŞÜK");
            tvVpdStatus.getBackground().setTint(getResources().getColor(R.color.water_blue, null));
            if (tvVpdAdvice != null) {
                tvVpdAdvice.setText("Sıcaklığı arttırın, Nemi düşürün, Havalandırmayı açın.");
            }
        } else if (vpd <= vpdMax) {
            tvVpdStatus.setText("İDEAL");
            tvVpdStatus.getBackground().setTint(getResources().getColor(R.color.vpd_ideal, null));
            if (tvVpdAdvice != null) {
                tvVpdAdvice.setText("Ortam değerleri mükemmel, müdahaleye gerek yok.");
            }
        } else {
            tvVpdStatus.setText("RİSKLİ");
            tvVpdStatus.getBackground().setTint(getResources().getColor(R.color.vpd_risk, null));
            if (tvVpdAdvice != null) {
                tvVpdAdvice.setText("Sıcaklığı düşürün, Nemi artırın, Gölgelendirme yapın.");
            }
        }
    }

    private double calculateVPD(double temp, double hum) {
        double es = 0.61078 * Math.exp((17.27 * temp) / (temp + 237.3));
        double ea = es * (hum / 100.0);
        return es - ea;
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), MqttManagerService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction("MQTT_DATA");
        filter.addAction("RECIPE_UPDATED");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mqttReceiver, filter);
        updateRecipeUI();
        
        if (sensorManager != null && gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mqttReceiver);
        
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isBound) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }
    
    private float currentAngle = 0f;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            
            double magnitudeXY = Math.sqrt(x * x + y * y);
            
            float targetAngle;
            // Eğer telefon masaya düz konmuşsa (X ve Y düzlemindeki çekim zayıfsa), varsayılan açıya (0) dön
            if (magnitudeXY < 2.5) {
                targetAngle = 0f;
            } else {
                targetAngle = (float) Math.toDegrees(Math.atan2(x, y));
            }
            
            // Açıyı yumuşatarak değiştir (Low-pass filter)
            float diff = targetAngle - currentAngle;
            while (diff > 180) diff -= 360;
            while (diff < -180) diff += 360;
            
            currentAngle += diff * 0.1f;
            
            if (liquidWaveView != null) {
                liquidWaveView.setTiltAngle(currentAngle);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}