package com.sametkapan.akillitarimnlp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.sametkapan.akillitarimnlp.models.Recipe;
import com.sametkapan.akillitarimnlp.models.RecipeManager;
import com.sametkapan.akillitarimnlp.nlp.CommandProcessor;
import com.sametkapan.akillitarimnlp.services.MqttManagerService;

import java.util.ArrayList;
import java.util.Locale;

public class AssistantFragment extends Fragment {

    private FrameLayout btnMic;
    private View bgWhiteCircle;
    private LottieAnimationView lottieWave;
    private TextView tvStatus, tvResGoogle, tvResWhisper;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private RotateAnimation rotateAnimation;
    private TextToSpeech tts;

    private MqttManagerService mqttService;
    private boolean isBound = false;

    private static final int PERMISSION_REQUEST_CODE = 100;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttManagerService.LocalBinder binder = (MqttManagerService.LocalBinder) service;
            mqttService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        // DİKKAT: Yapay Zeka Modelimizi Ayağa Kaldırıyoruz
        CommandProcessor.initialize(getContext());

        View view = inflater.inflate(R.layout.fragment_assistant, container, false);

        btnMic = view.findViewById(R.id.btnMic);
        bgWhiteCircle = view.findViewById(R.id.bgWhiteCircle);
        lottieWave = view.findViewById(R.id.lottieWave);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvResGoogle = view.findViewById(R.id.tvResGoogle);
        tvResWhisper = view.findViewById(R.id.tvResWhisper);

        // TTS Initialization
        tts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("tr", "TR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Dil desteklenmiyor.");
                }
            }
        });

        // Animation Setup
        rotateAnimation = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(1000);
        rotateAnimation.setRepeatCount(Animation.INFINITE);

        // STT Setup
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvStatus.setText("Dinleniyor...");
                startLottieAnimation();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                tvStatus.setText("İşleniyor...");
                stopLottieAnimation();
                startRotateAnimation();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                tvStatus.setText("Hata oluştu, tekrar deneyin.");
                stopLottieAnimation();
                stopRotateAnimation();
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                stopRotateAnimation();
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data != null && !data.isEmpty()) {
                    String text = data.get(0);
                    tvResGoogle.setText(text);

                    // YENİ NLP Katmanını Çalıştır
                    handleNLP(text);
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        btnMic.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (checkPermission()) {
                if (!isListening) {
                    startListening();
                } else {
                    stopListening();
                }
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
            }
        });

        return view;
    }

    private void handleNLP(String text) {
        CommandProcessor.CommandResult result = CommandProcessor.process(text);

        if (isBound && mqttService != null) {
            String finalResponse = result.response;
            switch (result.intent) {
                case PUMP_ON:
                    // 1. Pompayı açma komutunu yolla
                    mqttService.publishMessage(MqttManagerService.TOPIC_PUMP, "1");

                    // 2. Eğer NLP katmanı bir süre (saniye) tespit ettiyse geri sayım başlat
                    if (result.durationSeconds > 0) {
                        tvResWhisper.setText("Pompa Komutu: " + result.durationSeconds + " sn AÇIK");

                        // Handler ile asenkron bir gecikme (delay) oluşturuyoruz
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            // Süre dolduğunda servise bağlıysak kapatma komutu yolla
                            if (isBound && mqttService != null) {
                                mqttService.publishMessage(MqttManagerService.TOPIC_PUMP, "0");

                                // Kullanıcıya sesli ve yazılı bilgi ver
                                tvStatus.setText("Süreli sulama tamamlandı, pompa kapatıldı.");
                                speak("Süreli sulama tamamlandı.");
                                tvResWhisper.setText("Pompa Komutu: KAPALI");
                            }
                        }, result.durationSeconds * 1000L); // Saniyeyi milisaniyeye çevir

                    } else {
                        // Süre belirtilmemişse sürekli açık kalır
                        tvResWhisper.setText("Pompa Komutu: AÇIK");
                    }
                    break;
                case PUMP_OFF:
                    mqttService.publishMessage(MqttManagerService.TOPIC_PUMP, "0");
                    tvResWhisper.setText("Pompa Komutu: KAPALI");
                    break;
                case QUERY_STATUS:
                    finalResponse = generateStatusReport();
                    tvResWhisper.setText(finalResponse);
                    break;
                case QUERY_TEMP:
                    double currentTemp = mqttService.getLastTemp();
                    finalResponse = String.format(Locale.getDefault(), "Şu anki sıcaklık %.1f derece.", currentTemp);
                    tvResWhisper.setText("Sıcaklık : " + currentTemp);
                    break;

                case QUERY_MOISTURE:
                    double currentMoisture = mqttService.getLastMoisture();
                    finalResponse = String.format(Locale.getDefault(), "Toprak nemi yüzde %.1f.", currentMoisture);
                    tvResWhisper.setText("Toprak Nemi : " + currentMoisture);
                    break;
                case SET_AUTO_MODE:
                    mqttService.setAutoMode(true);
                    tvResWhisper.setText("Otonom Mod: AÇIK");
                    break;
                case SET_MANUAL_MODE:
                    mqttService.setAutoMode(false);
                    tvResWhisper.setText("Otonom Mod: KAPALI");
                    break;
                case SET_RECIPE:
                    if (result.recipeName != null) {
                        Recipe current = mqttService.getCurrentRecipe();
                        if (current != null && current.name.equals(result.recipeName)) {
                            finalResponse = "Zaten " + result.recipeName + " tarifindesiniz.";
                        } else {
                            Recipe target = null;
                            for (Recipe r : RecipeManager.getRecipes()) {
                                if (r.name.equals(result.recipeName)) {
                                    target = r;
                                    break;
                                }
                            }
                            if (target != null) {
                                mqttService.setRecipe(target, 1, true);
                                finalResponse = result.recipeName + " tarifine geçildi ve otonom mod başlatıldı.";
                            } else {
                                finalResponse = "Böyle bir tarif bulunamadı.";
                            }
                        }
                    }
                    tvResWhisper.setText("Tarif: " + result.recipeName);
                    break;
                case UNKNOWN:
                    tvResWhisper.setText("Anlaşılamadı.");
                    break;
            }
            tvStatus.setText(finalResponse);
            speak(finalResponse);
        } else {
            tvStatus.setText("Sistem henüz hazır değil, lütfen bekleyin.");
        }
    }

    private String generateStatusReport() {
        if (mqttService == null) return "Veri bağlantısı yok.";

        double temp = mqttService.getLastTemp();
        double hum = mqttService.getLastHum();
        double soil = mqttService.getLastMoisture();
        double vpd = mqttService.getLastVpd();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.getDefault(), "Sıcaklık %.1f derece, ", temp));
        sb.append(String.format(Locale.getDefault(), "Nem yüzde %.0f, ", hum));
        sb.append(String.format(Locale.getDefault(), "Toprak Nemi yüzde %.1f. ", soil));

        double vpdThresholdMax = 1.0;
        double vpdThresholdMin = 0.8;
        if (mqttService.isAutoMode() && mqttService.getCurrentPhase() != null) {
            vpdThresholdMax = mqttService.getCurrentPhase().targetVpdMax;
            vpdThresholdMin = mqttService.getCurrentPhase().targetVpdMin;
        }

        if (vpd > vpdThresholdMax) {
            sb.append("Bitkiler biraz susamış görünüyor, buhar basıncı açığı yüksek!");
        } else if (vpd < vpdThresholdMin) {
            sb.append("Bitkiler üşümüş olabilir, buhar basıncı açığı düşük!");
        } else {
            sb.append("Her şey yolunda görünüyor.");
        }

        return sb.toString();
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void startListening() {
        isListening = true;
        speechRecognizer.startListening(speechRecognizerIntent);
        btnMic.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200);
    }

    private void stopListening() {
        isListening = false;
        speechRecognizer.stopListening();
        btnMic.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200);
    }

    private void startLottieAnimation() {
        if (lottieWave != null) {
            lottieWave.setVisibility(View.VISIBLE);
            lottieWave.playAnimation();
            if (bgWhiteCircle != null) {
                bgWhiteCircle.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(300).start();
            }
        }
    }

    private void stopLottieAnimation() {
        if (lottieWave != null) {
            lottieWave.cancelAnimation();
            lottieWave.setVisibility(View.INVISIBLE);
            if (bgWhiteCircle != null) {
                bgWhiteCircle.animate().scaleX(0.5f).scaleY(0.5f).alpha(0.0f).setDuration(300).start();
            }
        }
    }

    private void startRotateAnimation() {
        btnMic.startAnimation(rotateAnimation);
    }

    private void stopRotateAnimation() {
        btnMic.clearAnimation();
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), MqttManagerService.class);
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isBound) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.shutdown();
        }
    }
}