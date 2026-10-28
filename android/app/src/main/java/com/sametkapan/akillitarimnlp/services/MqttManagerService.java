package com.sametkapan.akillitarimnlp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Context;
import android.content.SharedPreferences;

import com.sametkapan.akillitarimnlp.models.Recipe;
import com.sametkapan.akillitarimnlp.models.RecipeManager;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MqttManagerService extends Service {
    private static final String TAG = "MQTT_SERVICE";
    private MqttAsyncClient mqttClient;
    private final String serverUri = "tcp://broker.hivemq.com:1883";
    private final String clientId = "HassasTarimClient_" + System.currentTimeMillis();
    
    public static final String TOPIC_SENSOR = "tarim/sensorler";
    public static final String TOPIC_PUMP = "tarim/pompa";

    // --- Dijital İkiz Veri Yapısı ---
    public static class DataPoint implements Serializable {
        public long timestamp;
        public float vpd;
        public float moisture;
        public float temp;
        public float hum;
        public DataPoint(float vpd, float moisture, float temp, float hum) {
            this.timestamp = System.currentTimeMillis();
            this.vpd = vpd;
            this.moisture = moisture;
            this.temp = temp;
            this.hum = hum;
        }
    }
    private final List<DataPoint> history = Collections.synchronizedList(new ArrayList<>());
    private final int MAX_HISTORY_SIZE = 144; 

    // --- Son Sensör Verileri ---
    private double lastTemp = 0, lastHum = 0, lastMoisture = 0, lastVpd = 0;

    // --- Otonom Kontrol Durumu ---
    private Recipe currentRecipe;
    private int currentDay = 1;
    private boolean isAutoMode = false;
    
    private long lastIrrigationTime = 0;
    private long lastEmergencyIrrigationTime = 0;
    private long lastDayIncrementTime = 0;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MqttManagerService getService() { return MqttManagerService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification("Sistem takip ediliyor..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (currentRecipe == null) {
            restoreState();
        }
        if (history.isEmpty()) {
            restoreHistory();
        }
        if (mqttClient == null) initializeMqtt();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "AkilliTarimChannel",
                    "Akıllı Tarım Otonom Servisi",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, com.sametkapan.akillitarimnlp.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, "AkilliTarimChannel")
                .setContentTitle("Akıllı Tarım Asistanı")
                .setContentText(text)
                .setSmallIcon(com.sametkapan.akillitarimnlp.R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void initializeMqtt() {
        try {
            mqttClient = new MqttAsyncClient(serverUri, clientId, new MemoryPersistence());
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    subscribeToTopic(TOPIC_SENSOR);
                    subscribeToTopic(TOPIC_PUMP);
                    broadcastStatus("CONNECTED");
                    broadcastRecipeInfo();
                }

                @Override
                public void connectionLost(Throwable cause) { broadcastStatus("DISCONNECTED"); }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    if (TOPIC_SENSOR.equals(topic)) handleSensorData(payload);
                    
                    Intent intent = new Intent("MQTT_DATA");
                    intent.putExtra("topic", topic);
                    intent.putExtra("payload", payload);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                }

                @Override
                public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
            });
            connectToBroker();
        } catch (MqttException e) { Log.e(TAG, "Init Error", e); }
    }

    private void handleSensorData(String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            lastTemp = json.optDouble("sicaklik", 25.0);
            lastHum = json.optDouble("nem", 50.0);
            int s1 = json.optInt("toprak1", 0);
            int s2 = json.optInt("toprak2", 0);
            int s3 = json.optInt("toprak3", 0);
            
            double rawMoisture = ((s1 + s2 + s3) / 3.0 / 4095.0) * 100.0;
            lastMoisture = applyFilters(rawMoisture);
            lastVpd = calculateVPD(lastTemp, lastHum);
            
            long currentTime = System.currentTimeMillis();
            long lastSavedTime = history.isEmpty() ? 0 : history.get(history.size() - 1).timestamp;
            
            // Sadece her 5 dakikada (300.000 ms) bir grafiğe nokta ekle. 
            // Böylece 144 nokta tam olarak 12 saati temsil eder (12 * 60 / 5 = 144).
            if (currentTime - lastSavedTime >= 300000) {
                history.add(new DataPoint((float)lastVpd, (float)lastMoisture, (float)lastTemp, (float)lastHum));
                if (history.size() > MAX_HISTORY_SIZE) history.remove(0);
                saveHistory();
            }

            if (isAutoMode && currentRecipe != null) {
                if (lastDayIncrementTime == 0) {
                    lastDayIncrementTime = currentTime;
                }
                if (currentTime - lastDayIncrementTime >= 24L * 60L * 60L * 1000L) {
                    if (currentDay < currentRecipe.totalDays) {
                        currentDay++;
                        lastDayIncrementTime = currentTime;
                        saveState();
                        broadcastRecipeInfo();
                    }
                }
                
                runAutonomousLogic();
            }
            broadcastStats();
        } catch (Exception e) { Log.e(TAG, "Sensor Data Error", e); }
    }

    private void runAutonomousLogic() {
        Recipe.Phase phase = getCurrentPhase();
        if (phase == null) return;
        
        // 1. Acil Durum Kontrolü (Toprak nemi %10'un altındaysa)
        if (lastMoisture < 10.0) {
            java.util.Calendar todayCal = java.util.Calendar.getInstance();
            todayCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            todayCal.set(java.util.Calendar.MINUTE, 0);
            todayCal.set(java.util.Calendar.SECOND, 0);
            todayCal.set(java.util.Calendar.MILLISECOND, 0);
            
            java.util.Calendar lastEmergencyCal = java.util.Calendar.getInstance();
            if (lastEmergencyIrrigationTime > 0) {
                lastEmergencyCal.setTimeInMillis(lastEmergencyIrrigationTime);
                lastEmergencyCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                lastEmergencyCal.set(java.util.Calendar.MINUTE, 0);
                lastEmergencyCal.set(java.util.Calendar.SECOND, 0);
                lastEmergencyCal.set(java.util.Calendar.MILLISECOND, 0);
            } else {
                lastEmergencyCal.setTimeInMillis(0);
            }
            
            long diffEmergencyDays = (todayCal.getTimeInMillis() - lastEmergencyCal.getTimeInMillis()) / (24L * 60L * 60L * 1000L);
            
            // Eğer acil durum sulaması bugün yapılmadıysa (veya hiç yapılmadıysa)
            if (lastEmergencyIrrigationTime == 0 || diffEmergencyDays >= 1) {
                performPulseIrrigation(2); // 2 saniyelik acil sulama
                lastEmergencyIrrigationTime = System.currentTimeMillis();
                saveState();
                return; // Acil sulama yapıldıysa normal planlı sulamayı atla
            }
        }

        // 2. Normal Planlı Sulama Kontrolü
        if (phase.irrigationTimeOfDay == null) return;
        
        // Parse scheduled time (e.g., "12:30")
        String[] parts = phase.irrigationTimeOfDay.split(":");
        if (parts.length != 2) return;
        int targetHour = Integer.parseInt(parts[0]);
        int targetMinute = Integer.parseInt(parts[1]);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = cal.get(java.util.Calendar.MINUTE);
        
        boolean timeReached = (currentHour > targetHour) || (currentHour == targetHour && currentMinute >= targetMinute);
        if (!timeReached) return;

        java.util.Calendar lastIrrigCal = java.util.Calendar.getInstance();
        if (lastIrrigationTime > 0) {
            lastIrrigCal.setTimeInMillis(lastIrrigationTime);
            lastIrrigCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            lastIrrigCal.set(java.util.Calendar.MINUTE, 0);
            lastIrrigCal.set(java.util.Calendar.SECOND, 0);
            lastIrrigCal.set(java.util.Calendar.MILLISECOND, 0);
        } else {
            lastIrrigCal.setTimeInMillis(0);
        }

        java.util.Calendar todayCal = java.util.Calendar.getInstance();
        todayCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        todayCal.set(java.util.Calendar.MINUTE, 0);
        todayCal.set(java.util.Calendar.SECOND, 0);
        todayCal.set(java.util.Calendar.MILLISECOND, 0);

        long diffDays = (todayCal.getTimeInMillis() - lastIrrigCal.getTimeInMillis()) / (24L * 60L * 60L * 1000L);

        // Zamanı geldi mi ve sulama sıklığı (gün) geçti mi?
        if (lastIrrigationTime == 0 || diffDays >= phase.irrigationFrequencyDays) {
            performPulseIrrigation(phase.pulseDurationSec);
            lastIrrigationTime = System.currentTimeMillis();
            saveState(); // Update persisted lastIrrigationTime
        }
    }

    private void performPulseIrrigation(int seconds) {
        publishMessage(TOPIC_PUMP, "1");
        mainHandler.postDelayed(() -> {
            publishMessage(TOPIC_PUMP, "0");
            lastIrrigationTime = System.currentTimeMillis();
        }, seconds * 1000L);
    }

    private double calculateVPD(double temp, double hum) {
        double es = 0.6108 * Math.exp((17.27 * temp) / (temp + 237.3));
        return es * (1 - hum / 100.0);
    }

    public void setAutoMode(boolean auto) {
        this.isAutoMode = auto;
        saveState();
        broadcastRecipeInfo();
    }

    public void resetRecipeDay() {
        this.currentDay = 1;
        this.lastDayIncrementTime = System.currentTimeMillis();
        saveState();
        broadcastRecipeInfo();
    }
    
    public void setRecipeDay(int newDay) {
        if (currentRecipe != null) {
            this.currentDay = Math.max(1, Math.min(currentRecipe.totalDays, newDay));
            this.lastDayIncrementTime = System.currentTimeMillis();
            saveState();
            broadcastRecipeInfo();
        }
    }

    public void setRecipe(Recipe recipe, int day, boolean auto) {
        this.currentRecipe = recipe;
        this.currentDay = day;
        this.isAutoMode = auto;
        this.lastDayIncrementTime = System.currentTimeMillis();
        saveState();
        broadcastRecipeInfo();
    }

    public List<DataPoint> getHistory() { return new ArrayList<>(history); }
    public Recipe getCurrentRecipe() { return currentRecipe; }
    public boolean isAutoMode() { return isAutoMode; }
    public int getCurrentDay() { return currentDay; }
    public double getLastTemp() { return lastTemp; }
    public double getLastHum() { return lastHum; }
    public double getLastMoisture() { return lastMoisture; }
    public double getLastVpd() { return lastVpd; }

    private void broadcastRecipeInfo() {
        Intent intent = new Intent("RECIPE_UPDATED");
        intent.putExtra("recipeName", currentRecipe != null ? currentRecipe.name : "Manuel Kontrol");
        intent.putExtra("currentDay", currentDay);
        intent.putExtra("isAuto", isAutoMode);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void broadcastStats() {
        Intent intent = new Intent("MQTT_STATS");
        intent.putExtra("learningRate", 1.0);
        intent.putExtra("systemLocked", false);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void broadcastStatus(String status) {
        Intent intent = new Intent("MQTT_STATUS");
        intent.putExtra("status", status);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }
    
    private void saveState() {
        SharedPreferences prefs = getSharedPreferences("AkilliTarimPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (currentRecipe != null) {
            editor.putString("activeRecipeName", currentRecipe.name);
            editor.putInt("currentDay", currentDay);
            editor.putBoolean("isAutoMode", isAutoMode);
            editor.putLong("lastIrrigationTime", lastIrrigationTime);
            editor.putLong("lastEmergencyIrrigationTime", lastEmergencyIrrigationTime);
            editor.putLong("lastDayIncrementTime", lastDayIncrementTime);
        } else {
            editor.remove("activeRecipeName");
        }
        editor.apply();
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences("AkilliTarimPrefs", Context.MODE_PRIVATE);
        String savedRecipeName = prefs.getString("activeRecipeName", null);
        if (savedRecipeName != null) {
            for (Recipe r : RecipeManager.getRecipes()) {
                if (r.name.equals(savedRecipeName)) {
                    this.currentRecipe = r;
                    this.currentDay = prefs.getInt("currentDay", 1);
                    this.isAutoMode = prefs.getBoolean("isAutoMode", false);
                    this.lastIrrigationTime = prefs.getLong("lastIrrigationTime", 0);
                    this.lastEmergencyIrrigationTime = prefs.getLong("lastEmergencyIrrigationTime", 0);
                    this.lastDayIncrementTime = prefs.getLong("lastDayIncrementTime", System.currentTimeMillis());
                    break;
                }
            }
        }
    }
    
    private void saveHistory() {
        SharedPreferences prefs = getSharedPreferences("AkilliTarimPrefs", Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();
        synchronized(history) {
            for (DataPoint dp : history) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("t", dp.timestamp);
                    obj.put("v", dp.vpd);
                    obj.put("m", dp.moisture);
                    obj.put("te", dp.temp);
                    obj.put("h", dp.hum);
                    jsonArray.put(obj);
                } catch (Exception ignored) {}
            }
        }
        prefs.edit().putString("sensorHistory", jsonArray.toString()).apply();
    }

    private void restoreHistory() {
        SharedPreferences prefs = getSharedPreferences("AkilliTarimPrefs", Context.MODE_PRIVATE);
        String historyStr = prefs.getString("sensorHistory", null);
        if (historyStr != null) {
            try {
                JSONArray jsonArray = new JSONArray(historyStr);
                history.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    float v = (float) obj.optDouble("v", 0.0);
                    float m = (float) obj.optDouble("m", 0.0);
                    float te = (float) obj.optDouble("te", 0.0);
                    float h = (float) obj.optDouble("h", 0.0);
                    DataPoint dp = new DataPoint(v, m, te, h);
                    dp.timestamp = obj.optLong("t", System.currentTimeMillis());
                    history.add(dp);
                }
            } catch (Exception ignored) {}
        }
    }

    private double applyFilters(double rawValue) {
        // Just return the raw value since we removed the EMA array
        return rawValue;
    }

    private void connectToBroker() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        try {
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken asyncActionToken) { broadcastStatus("CONNECTED"); }
                @Override public void onFailure(IMqttToken asyncActionToken, Throwable exception) { broadcastStatus("DISCONNECTED"); }
            });
        } catch (MqttException e) { e.printStackTrace(); }
    }

    private void subscribeToTopic(String topic) {
        try { mqttClient.subscribe(topic, 0); } catch (MqttException e) { e.printStackTrace(); }
    }

    public void publishMessage(String topic, String message) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.publish(topic, message.getBytes(), 0, false);
            }
        } catch (MqttException e) { Log.e(TAG, "Publish Error", e); }
    }

    public Recipe.Phase getCurrentPhase() {
        if (currentRecipe == null) return null;
        for (Recipe.Phase p : currentRecipe.phases) {
            if (currentDay >= p.startDay && currentDay <= p.endDay) return p;
        }
        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (mqttClient != null) { mqttClient.disconnect(); mqttClient.close(); } } catch (MqttException e) { e.printStackTrace(); }
    }
}
