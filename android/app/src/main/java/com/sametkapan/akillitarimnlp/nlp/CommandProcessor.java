package com.sametkapan.akillitarimnlp.nlp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {

    public enum Intent {
        PUMP_ON,
        PUMP_OFF,
        QUERY_STATUS,
        QUERY_TEMP,
        QUERY_MOISTURE,
        SET_AUTO_MODE,
        SET_MANUAL_MODE,
        SET_RECIPE,
        UNKNOWN
    }

    public static class CommandResult {
        public Intent intent;
        public int durationSeconds = 0;
        public String response;
        public String recipeName = null;

        public CommandResult(Intent intent, String response) {
            this.intent = intent;
            this.response = response;
        }
    }

    private static Interpreter tflite;
    private static final Map<String, Integer> vocab = new HashMap<>();

    // Python'daki max_len değişkeni ile aynı olmalı
    private static final int MAX_LEN = 5;

    // Yapay Zeka Kelime Dağarcığı (220 Kelime)
    static {
        vocab.put("pompayi", 1); vocab.put("ac", 2); vocab.put("suyu", 3); vocab.put("sulamayi", 4); vocab.put("baslat", 5);
        vocab.put("motoru", 6); vocab.put("calistir", 7); vocab.put("devreye", 8); vocab.put("al", 9); vocab.put("su", 10);
        vocab.put("ver", 11); vocab.put("sal", 12); vocab.put("pompa", 13); vocab.put("calissin", 14); vocab.put("sulamaya", 15);
        vocab.put("basla", 16); vocab.put("lutfen", 17); vocab.put("hadi", 18); vocab.put("bitkilere", 19); vocab.put("atesle", 20);
        vocab.put("sisteme", 21); vocab.put("aktif", 22); vocab.put("et", 23); vocab.put("pompaya", 24); vocab.put("guc", 25);
        vocab.put("sok", 26); vocab.put("aciver", 27); vocab.put("sulama", 28); vocab.put("motorunu", 29); vocab.put("ciceklere", 30);
        vocab.put("tetikle", 31); vocab.put("vanayi", 32); vocab.put("pompasini", 33); vocab.put("baslatsana", 34); vocab.put("biraz", 35);
        vocab.put("bitkileri", 36); vocab.put("sula", 37); vocab.put("sistemi", 38); vocab.put("kapat", 39); vocab.put("durdur", 40);
        vocab.put("kes", 41); vocab.put("bitir", 42); vocab.put("iptal", 43); vocab.put("devre", 44); vocab.put("disi", 45);
        vocab.put("birak", 46); vocab.put("yeterli", 47); vocab.put("kessene", 48); vocab.put("verme", 49); vocab.put("sonlandir", 50);
        vocab.put("kapa", 51); vocab.put("kesiver", 52); vocab.put("islemini", 53); vocab.put("durdursana", 54); vocab.put("artik", 55);
        vocab.put("sistemin", 56); vocab.put("suyunu", 57); vocab.put("bitsin", 58); vocab.put("akisini", 59); vocab.put("sustur", 60);
        vocab.put("duraklat", 61); vocab.put("durum", 62); vocab.put("ne", 63); vocab.put("sistem", 64); vocab.put("nasil", 65);
        vocab.put("degerleri", 66); vocab.put("oku", 67); vocab.put("bitki", 68); vocab.put("sensor", 69); vocab.put("alemdeyiz", 70);
        vocab.put("durumu", 71); vocab.put("nedir", 72); vocab.put("her", 73); vocab.put("sey", 74); vocab.put("yolunda", 75);
        vocab.put("mi", 76); vocab.put("durumlar", 77); vocab.put("rapor", 78); vocab.put("durumda", 79); vocab.put("bitkilerin", 80);
        vocab.put("genel", 81); vocab.put("duruma", 82); vocab.put("bak", 83); vocab.put("kontrol", 84); vocab.put("bitkiler", 85);
        vocab.put("sagligi", 86); vocab.put("bana", 87); vocab.put("raporla", 88); vocab.put("sera", 89); vocab.put("alemde", 90);
        vocab.put("raporu", 91); vocab.put("alayim", 92); vocab.put("guncel", 93); vocab.put("sensorler", 94); vocab.put("diyor", 95);
        vocab.put("iyi", 96); vocab.put("herhangi", 97); vocab.put("bir", 98); vocab.put("sorun", 99); vocab.put("var", 100);
        vocab.put("isler", 101); vocab.put("bilgisini", 102); vocab.put("verileri", 103); vocab.put("goster", 104); vocab.put("son", 105);
        vocab.put("degerlerini", 106); vocab.put("soyle", 107); vocab.put("sicaklik", 108); vocab.put("kac", 109); vocab.put("derece", 110);
        vocab.put("sicak", 111); vocab.put("hava", 112); vocab.put("icerisi", 113); vocab.put("isi", 114); vocab.put("oldu", 115);
        vocab.put("ortam", 116); vocab.put("isisi", 117); vocab.put("dereceyi", 118); vocab.put("degeri", 119); vocab.put("suan", 120);
        vocab.put("soguk", 121); vocab.put("mu", 122); vocab.put("durumunu", 123); vocab.put("an", 124); vocab.put("sicakligi", 125);
        vocab.put("dusuk", 126); vocab.put("normal", 127); vocab.put("santigrat", 128); vocab.put("havanin", 129); vocab.put("suanki", 130);
        vocab.put("degerini", 131); vocab.put("soyler", 132); vocab.put("misin", 133); vocab.put("nem", 134); vocab.put("toprak", 135);
        vocab.put("kuru", 136); vocab.put("islak", 137); vocab.put("lazim", 138); vocab.put("nemi", 139); vocab.put("nemli", 140);
        vocab.put("kurumus", 141); vocab.put("orani", 142); vocab.put("susamislar", 143); vocab.put("seviyesi", 144); vocab.put("neme", 145);
        vocab.put("baksana", 146); vocab.put("kurudu", 147); vocab.put("yuzde", 148); vocab.put("rutubet", 149); vocab.put("topraga", 150);
        vocab.put("susamis", 151); vocab.put("miktari", 152); vocab.put("topragin", 153); vocab.put("otonom", 154); vocab.put("moda", 155);
        vocab.put("gec", 156); vocab.put("akilli", 157); vocab.put("modu", 158); vocab.put("otonomu", 159); vocab.put("yap", 160);
        vocab.put("kendin", 161); vocab.put("yonet", 162); vocab.put("otomatik", 163); vocab.put("akillandir", 164); vocab.put("kendi", 165);
        vocab.put("kendine", 166); vocab.put("sen", 167); vocab.put("devral", 168); vocab.put("tarim", 169); vocab.put("moduna", 170);
        vocab.put("calis", 171); vocab.put("otomatigi", 172); vocab.put("kendisi", 173); vocab.put("sulasin", 174); vocab.put("yapay", 175);
        vocab.put("zekaya", 176); vocab.put("devret", 177); vocab.put("yonetime", 178); vocab.put("serbest", 179); vocab.put("yonetimi", 180);
        vocab.put("tarimi", 181); vocab.put("mod", 182); vocab.put("sekilde", 183); vocab.put("manuel", 184); vocab.put("el", 185);
        vocab.put("ile", 186); vocab.put("kontrole", 187); vocab.put("elle", 188); vocab.put("ben", 189); vocab.put("yonetecegim", 190);
        vocab.put("otonomdan", 191); vocab.put("cik", 192); vocab.put("sulayacagim", 193); vocab.put("manuele", 194); vocab.put("kendim", 195);
        vocab.put("edecegim", 196); vocab.put("yonetim", 197); vocab.put("yapacagim", 198); vocab.put("kontrolu", 199); vocab.put("yonetiyorum", 200);
        vocab.put("kontrolune", 201); vocab.put("kontrolume", 202); vocab.put("aliyorum", 203); vocab.put("turp", 204); vocab.put("tarifine", 205);
        vocab.put("ev", 206); vocab.put("bitkisi", 207); vocab.put("sec", 208); vocab.put("tarif", 209); vocab.put("degistir", 210);
        vocab.put("ekildi", 211); vocab.put("tarifi", 212); vocab.put("mikro", 213); vocab.put("yesillik", 214); vocab.put("yetistirecegim", 215);
        vocab.put("recetesini", 216); vocab.put("bitkisine", 217); vocab.put("ekimi", 218); vocab.put("basladi", 219); vocab.put("secimini", 220);
    }

    // Uygulama açılırken modeli belleğe yüklemek için (AssistantFragment içinde çağrılır)
    public static void initialize(Context context) {
        if (tflite != null) return; // Zaten yüklüyse tekrar yükleme
        try {
            tflite = new Interpreter(loadModelFile(context));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static CommandResult process(String rawText) {
        if (tflite == null) {
            return new CommandResult(Intent.UNKNOWN, "Yapay zeka modeli yüklenemedi. Lütfen assets klasörünü kontrol et.");
        }

        String cleanText = preprocess(rawText);
        String[] words = cleanText.split("\\s+");

        org.tensorflow.lite.Tensor inTensor = tflite.getInputTensor(0);
        org.tensorflow.lite.Tensor outTensor = tflite.getOutputTensor(0);
        int maxLen = inTensor.shape()[1];
        int outClasses = outTensor.shape()[1];
        boolean isInputFloat = (inTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32);

        // 1. Girdi dizisini hazırla (Tokenization & Padding)
        Object inputObj;
        if (isInputFloat) {
            float[][] inputVal = new float[1][maxLen];
            for (int i = 0; i < maxLen; i++) {
                if (i < words.length) {
                    Integer id = vocab.get(words[i]);
                    inputVal[0][i] = (id != null) ? id : 0;
                } else {
                    inputVal[0][i] = 0;
                }
            }
            inputObj = inputVal;
        } else {
            int[][] inputVal = new int[1][maxLen];
            for (int i = 0; i < maxLen; i++) {
                if (i < words.length) {
                    Integer id = vocab.get(words[i]);
                    inputVal[0][i] = (id != null) ? id : 0;
                } else {
                    inputVal[0][i] = 0;
                }
            }
            inputObj = inputVal;
        }

        // 2. Çıktı matrisini hazırla
        float[][] outputVal = new float[1][outClasses];

        int maxIdx = -1;
        float maxProb = -1.0f;

        try {
            // 3. Model çıkarımını yap (Inference)
            tflite.run(inputObj, outputVal);

            // 4. En yüksek olasılıklı niyet indexini bul (Argmax)
            for (int i = 0; i < outClasses; i++) {
                if (outputVal[0][i] > maxProb) {
                    maxProb = outputVal[0][i];
                    maxIdx = i;
                }
            }
        } catch (Exception e) {
            return new CommandResult(Intent.UNKNOWN, "Model hatası: " + e.getMessage() + " InType:" + inTensor.dataType());
        }

        // Güven eşiği (Model %65'ten az eminse işlem yapma)
        if (maxProb < 0.65f) {
            return new CommandResult(Intent.UNKNOWN, "Üzgünüm, bunu tam anlayamadım. Daha net komutlar verebilirsin.");
        }

        Intent predictedIntent;
        if (maxIdx >= 0 && maxIdx < Intent.values().length) {
            predictedIntent = Intent.values()[maxIdx];
        } else {
            predictedIntent = Intent.UNKNOWN;
        }

        // 5. Niyete Göre Aksiyon ve Cevaplar
        switch (predictedIntent) {
            case PUMP_ON:
                int seconds = extractDurationInSeconds(cleanText);
                if (seconds > 0) {
                    String timeLabel = seconds >= 60 ? (seconds / 60) + " dakika" : seconds + " saniye";
                    CommandResult res = new CommandResult(Intent.PUMP_ON, "Anlaşıldı, pompayı " + timeLabel + " boyunca çalıştırıyorum.");
                    res.durationSeconds = seconds;
                    return res;
                }
                return new CommandResult(Intent.PUMP_ON, "Anlaşıldı, sulamayı başlatıyorum. Otonom modu devre dışı bıraktım.");

            case PUMP_OFF:
                return new CommandResult(Intent.PUMP_OFF, "Tamamdır, su pompasını kapattım.");

            case QUERY_STATUS:
                return new CommandResult(Intent.QUERY_STATUS, "");

            case QUERY_TEMP:
                return new CommandResult(Intent.QUERY_TEMP, "");

            case QUERY_MOISTURE:
                return new CommandResult(Intent.QUERY_MOISTURE, "");

            case SET_AUTO_MODE:
                return new CommandResult(Intent.SET_AUTO_MODE, "Sistemi otonom moda geçiriyorum. Bitki tarifine göre otomatik yönetim başlayacak.");

            case SET_MANUAL_MODE:
                return new CommandResult(Intent.SET_MANUAL_MODE, "Sistem artık manuel kontrolünde.");

            case SET_RECIPE:
                CommandResult resRecipe = new CommandResult(Intent.SET_RECIPE, "");
                // Özel tarifi metinden çıkartma (Regex kuralı burada hala işe yarar)
                if (cleanText.contains("turp")) {
                    resRecipe.recipeName = "Microgreen Turp (20 Gün)";
                } else if (cleanText.contains("ev bitkisi") || cleanText.contains("genel") || cleanText.contains("saksi")) {
                    resRecipe.recipeName = "Genel Ev Bitkisi";
                } else {
                    resRecipe.recipeName = "Genel Ev Bitkisi"; // Varsayılan
                }
                return resRecipe;

            default:
                return new CommandResult(Intent.UNKNOWN, "Üzgünüm, bunu tam anlayamadım.");
        }
    }

    private static String preprocess(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replace("ı", "i")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace("ş", "s")
                .replace("ö", "o")
                .replace("ç", "c")
                .replaceAll("[.,!?]", "") // Noktalama işaretlerini temizle
                .trim();
    }

    private static int extractDurationInSeconds(String text) {
        String processedText = text.replace("bir", "1")
                .replace("iki", "2")
                .replace("üç", "3")
                .replace("dört", "4")
                .replace("beş", "5");

        Pattern pattern = Pattern.compile("(\\d+)\\s*(dakika|dk|saniye|sn)");
        Matcher matcher = pattern.matcher(processedText);

        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                if (unit.contains("dakika") || unit.contains("dk")) {
                    return value * 60;
                } else {
                    return value;
                }
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
