package com.sametkapan.akillitarimnlp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.sametkapan.akillitarimnlp.services.MqttManagerService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AnalysisFragment extends Fragment {

    private TextView tvLearningRate, tvSystemStatus, tvNextIrrigation;
    private AutoCompleteTextView spinnerChartType;
    private LineChart lineChart;
    private MqttManagerService mqttService;
    private boolean isBound = false;
    private int currentChartTypeIndex = 0;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttManagerService.LocalBinder binder = (MqttManagerService.LocalBinder) service;
            mqttService = binder.getService();
            isBound = true;
            updateChart();
            updateStatsUI(1.0, false);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver statsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("MQTT_STATS".equals(action)) {
                double rate = intent.getDoubleExtra("learningRate", 0.0);
                boolean locked = intent.getBooleanExtra("systemLocked", false);
                updateStatsUI(rate, locked);
                updateChart();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_analysis, container, false);

        tvLearningRate = view.findViewById(R.id.tvLearningRate);
        tvSystemStatus = view.findViewById(R.id.tvSystemStatus);
        tvNextIrrigation = view.findViewById(R.id.tvNextIrrigation);
        lineChart = view.findViewById(R.id.lineChart);
        spinnerChartType = view.findViewById(R.id.spinnerChartType);

        String[] chartTypes = new String[]{"Toprak Nemi (%)", "Sıcaklık (°C)", "VPD Değeri (kPa)", "Hava Nemi (%)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, chartTypes);
        spinnerChartType.setAdapter(adapter);
        spinnerChartType.setText(chartTypes[0], false);
        
        spinnerChartType.setOnItemClickListener((parent, v, position, id) -> {
            currentChartTypeIndex = position;
            updateChart();
        });

        setupChart();

        return view;
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.setDrawGridBackground(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(Color.GRAY);

        lineChart.getAxisLeft().setTextColor(Color.GRAY);
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().enableGridDashedLine(10f, 10f, 0f);
        lineChart.getAxisLeft().setDrawAxisLine(false);
        
        lineChart.getAxisRight().setEnabled(false);
        
        lineChart.getLegend().setTextColor(Color.DKGRAY);
        lineChart.getLegend().setTextSize(12f);
        lineChart.getLegend().setForm(com.github.mikephil.charting.components.Legend.LegendForm.LINE);
    }

    private void updateChart() {
        if (!isBound || mqttService == null) return;

        List<MqttManagerService.DataPoint> history = mqttService.getHistory();
        if (history.isEmpty()) return;

        ArrayList<Entry> entries = new ArrayList<>();

        for (int i = 0; i < history.size(); i++) {
            MqttManagerService.DataPoint dp = history.get(i);
            float value = 0;
            if (currentChartTypeIndex == 0) value = dp.moisture;
            else if (currentChartTypeIndex == 1) value = dp.temp;
            else if (currentChartTypeIndex == 2) value = dp.vpd;
            else if (currentChartTypeIndex == 3) value = dp.hum;
            
            entries.add(new Entry(i, value));
        }

        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < history.size()) {
                    long timestamp = history.get(index).timestamp;
                    return sdf.format(new Date(timestamp));
                }
                return "";
            }
        });
        lineChart.getXAxis().setLabelCount(5, false);

        String label = "";
        String colorHex = "#00B4DB";
        if (currentChartTypeIndex == 0) {
            label = "Toprak Nemi (%)";
            colorHex = "#2D5A27";
        } else if (currentChartTypeIndex == 1) {
            label = "Sıcaklık (°C)";
            colorHex = "#FFB74D";
        } else if (currentChartTypeIndex == 2) {
            label = "VPD Değeri (kPa)";
            colorHex = "#00B4DB";
        } else if (currentChartTypeIndex == 3) {
            label = "Hava Nemi (%)";
            colorHex = "#81D4FA";
        }

        LineDataSet set = new LineDataSet(entries, label);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setColor(Color.parseColor(colorHex));
        set.setLineWidth(3f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillColor(Color.parseColor(colorHex));
        set.setFillAlpha(50);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(set);

        boolean isInitial = lineChart.getData() == null;
        LineData data = new LineData(dataSets);
        lineChart.setData(data);
        
        // Eksen sınırlarını tipe göre ayarla
        lineChart.getAxisLeft().resetAxisMinimum();
        lineChart.getAxisLeft().resetAxisMaximum();
        if (currentChartTypeIndex == 0 || currentChartTypeIndex == 3) {
            lineChart.getAxisLeft().setAxisMinimum(0f);
            lineChart.getAxisLeft().setAxisMaximum(100f);
        } else if (currentChartTypeIndex == 2) {
            lineChart.getAxisLeft().setAxisMinimum(0f);
            lineChart.getAxisLeft().setAxisMaximum(3f);
        }

        if (isInitial) {
            lineChart.animateX(1000);
        } else {
            lineChart.invalidate(); 
        }
    }

    private void updateStatsUI(double rate, boolean locked) {
        tvLearningRate.setText("• Otonom Kontrol: Zamanlı ve Güvenli");
        
        if (locked) {
            tvSystemStatus.setText("• Sistem Durumu: KRİTİK HATA (Kilitlendi)");
            tvSystemStatus.setTextColor(Color.RED);
            tvNextIrrigation.setText("• Tahmini Davranış: Sulama durduruldu. Sensörleri kontrol edin.");
        } else {
            tvSystemStatus.setText("• Sistem Durumu: Normal (Zamanlı)");
            tvSystemStatus.setTextColor(Color.parseColor("#2D5A27"));
            tvNextIrrigation.setText("• Tahmini Davranış: Planlanan gün ve saatte sulama yapılacaktır.");
        }
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
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(statsReceiver, new IntentFilter("MQTT_STATS"));
        updateChart();
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(statsReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isBound) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }
}
