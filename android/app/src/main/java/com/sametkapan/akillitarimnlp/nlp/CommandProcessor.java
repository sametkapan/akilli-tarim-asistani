package com.sametkapan.akillitarimnlp.nlp;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {

    public enum Intent {
        PUMP_ON,         // 0
        PUMP_OFF,        // 1
        QUERY_STATUS,    // 2
        QUERY_TEMP,      // 3
        QUERY_MOISTURE,  // 4
        SET_AUTO_MODE,   // 5
        SET_MANUAL_MODE, // 6
        SET_RECIPE,      // 7
        UNKNOWN          // 8
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

    private static final Map<String, Integer> vocab = new HashMap<>();
    private static final int MAX_LEN = 5;
    private static final int NUM_CLASSES = 8;
    private static final int VOCAB_SIZE = 221; // 1 (Padding) + 220 Kelime

    // LINEAR SVM MATRİS PARAMETRELERİ
    // Python'daki model.coef_ ve model.intercept_ değerleri buraya gelecek
    private static float[][] SVM_WEIGHTS = new float[NUM_CLASSES][VOCAB_SIZE * MAX_LEN];
    private static float[] SVM_BIASES = new float[NUM_CLASSES];

    static {
        // Yapay Zeka Kelime Dağarcığı
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

        // PYTHON ÇIKTISI OLAN MATRİS BLOKLARI BURAYA GELECEK
        // ÖRNEK OLARAK BAŞLANGIÇTA BOŞ BIRAKIYORUZ, PYTHON KODU BURAYI DOLDURACAK
        initializeSvmParameters();
    }

    public static CommandResult process(String rawText) {
        String cleanText = preprocess(rawText);
        String[] words = cleanText.split("\\s+");

        // 1. Girdi Vektörünü Hazırla (One-Hot / Flat Tokenization)
        // SVM doğrusal uzay beklediği için giriş dizisini tek boyuta (flatten) açıyoruz
        float[] xFlat = new float[MAX_LEN * VOCAB_SIZE];
        for (int i = 0; i < MAX_LEN; i++) {
            int wordToken = 0; // Padding varsayılan değeri
            if (i < words.length) {
                Integer id = vocab.get(words[i]);
                wordToken = (id != null) ? id : 0;
            }
            // One-Hot Encoding mantığıyla ilgili hücreyi 1 yapıyoruz
            if (wordToken < VOCAB_SIZE) {
                xFlat[i * VOCAB_SIZE + wordToken] = 1.0f;
            }
        }

        // 2. SVM Hiper-Düzlem Çarpımı (Matris Çarpımı: Y = W * X + B)
        float[] decisionScores = new float[NUM_CLASSES];
        int maxIdx = -1;
        float maxScore = -Float.MAX_VALUE;

        for (int c = 0; c < NUM_CLASSES; c++) {
            float score = SVM_BIASES[c]; // Bias ile başla
            for (int f = 0; f < xFlat.length; f++) {
                score += xFlat[f] * SVM_WEIGHTS[c][f]; // Vektörel çarpım
            }
            decisionScores[c] = score;

            if (score > maxScore) {
                maxScore = score;
                maxIdx = c;
            }
        }

        // Güvenilirlik Sınırı (SVM skoru sıfırdan küçükse düzlemin yanlış tarafındadır)
        if (maxScore < 0.0f) {
            return new CommandResult(Intent.UNKNOWN, "Üzgünüm, bunu tam anlayamadım. Daha net komutlar verebilirsin.");
        }

        Intent predictedIntent = (maxIdx >= 0 && maxIdx < Intent.values().length) ? Intent.values()[maxIdx] : Intent.UNKNOWN;

        // 3. Niyete Göre Aksiyon ve Cevaplar
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
                if (cleanText.contains("turp")) {
                    resRecipe.recipeName = "Microgreen Turp (20 Gün)";
                } else if (cleanText.contains("ev bitkisi") || cleanText.contains("genel") || cleanText.contains("saksi")) {
                    resRecipe.recipeName = "Genel Ev Bitkisi";
                } else {
                    resRecipe.recipeName = "Genel Ev Bitkisi";
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
                .replaceAll("[.,!?]", "")
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

    // --- BU ÇIKTIYI JAVA CommandProcessor.java İÇİNDEKİ METODA YAPIŞTIR ---

    private static void initializeSvmParameters() {
        SVM_BIASES[0] = -0.297586f;
        SVM_BIASES[1] = -0.279228f;
        SVM_BIASES[2] = -0.303428f;
        SVM_BIASES[3] = -0.293002f;
        SVM_BIASES[4] = -0.246592f;
        SVM_BIASES[5] = -0.230653f;
        SVM_BIASES[6] = -0.127864f;
        SVM_BIASES[7] = -0.210476f;


        SVM_WEIGHTS[0][1] = 0.200326f;
        SVM_WEIGHTS[0][3] = 0.200384f;
        SVM_WEIGHTS[0][4] = 0.333629f;
        SVM_WEIGHTS[0][62] = -0.110919f;
        SVM_WEIGHTS[0][64] = -0.110914f;
        SVM_WEIGHTS[0][108] = -0.166374f;
        SVM_WEIGHTS[0][110] = -0.110910f;
        SVM_WEIGHTS[0][134] = -0.110914f;
        SVM_WEIGHTS[0][135] = -0.069485f;
        SVM_WEIGHTS[0][154] = -0.032066f;
        SVM_WEIGHTS[0][157] = -0.069481f;
        SVM_WEIGHTS[0][184] = -0.032071f;
        SVM_WEIGHTS[0][185] = -0.101207f;
        SVM_WEIGHTS[0][204] = -0.048105f;
        SVM_WEIGHTS[0][206] = -0.069479f;
        SVM_WEIGHTS[0][223] = 1.466904f;
        SVM_WEIGHTS[0][226] = 1.000000f;
        SVM_WEIGHTS[0][260] = -0.533147f;
        SVM_WEIGHTS[0][261] = -0.533048f;
        SVM_WEIGHTS[0][262] = -0.666371f;
        SVM_WEIGHTS[0][284] = -0.221829f;
        SVM_WEIGHTS[0][286] = -0.221828f;
        SVM_WEIGHTS[0][330] = -0.166374f;
        SVM_WEIGHTS[0][357] = -0.069485f;
        SVM_WEIGHTS[0][376] = -0.064137f;
        SVM_WEIGHTS[0][379] = -0.069481f;
        SVM_WEIGHTS[0][407] = -0.101207f;
        SVM_WEIGHTS[0][426] = -0.048105f;
        SVM_WEIGHTS[0][428] = -0.069479f;
        SVM_WEIGHTS[0][442] = 0.124308f;
        SVM_WEIGHTS[0][444] = -0.069481f;
        SVM_WEIGHTS[0][564] = -0.069485f;
        SVM_WEIGHTS[0][598] = -0.112242f;
        SVM_WEIGHTS[0][629] = -0.101207f;
        SVM_WEIGHTS[0][650] = -0.069479f;
        SVM_WEIGHTS[0][663] = -0.196378f;
        SVM_WEIGHTS[0][819] = -0.101207f;
        SVM_WEIGHTS[0][884] = -0.297586f;
        SVM_WEIGHTS[1][1] = 0.485944f;
        SVM_WEIGHTS[1][3] = 0.485995f;
        SVM_WEIGHTS[1][4] = 0.238307f;
        SVM_WEIGHTS[1][62] = -0.174462f;
        SVM_WEIGHTS[1][64] = -0.174467f;
        SVM_WEIGHTS[1][108] = -0.261695f;
        SVM_WEIGHTS[1][110] = -0.174463f;
        SVM_WEIGHTS[1][134] = -0.174466f;
        SVM_WEIGHTS[1][135] = -0.090901f;
        SVM_WEIGHTS[1][154] = -0.041954f;
        SVM_WEIGHTS[1][157] = -0.090900f;
        SVM_WEIGHTS[1][184] = -0.041955f;
        SVM_WEIGHTS[1][185] = -0.110386f;
        SVM_WEIGHTS[1][204] = -0.062928f;
        SVM_WEIGHTS[1][206] = -0.090899f;
        SVM_WEIGHTS[1][223] = -1.009352f;
        SVM_WEIGHTS[1][226] = -0.761693f;
        SVM_WEIGHTS[1][260] = 0.990598f;
        SVM_WEIGHTS[1][261] = 0.990692f;
        SVM_WEIGHTS[1][262] = 1.000000f;
        SVM_WEIGHTS[1][284] = -0.348925f;
        SVM_WEIGHTS[1][286] = -0.348933f;
        SVM_WEIGHTS[1][330] = -0.261695f;
        SVM_WEIGHTS[1][357] = -0.090901f;
        SVM_WEIGHTS[1][376] = -0.083908f;
        SVM_WEIGHTS[1][379] = -0.090900f;
        SVM_WEIGHTS[1][407] = -0.110386f;
        SVM_WEIGHTS[1][426] = -0.062928f;
        SVM_WEIGHTS[1][428] = -0.090899f;
        SVM_WEIGHTS[1][442] = 0.250693f;
        SVM_WEIGHTS[1][444] = -0.090900f;
        SVM_WEIGHTS[1][564] = -0.090901f;
        SVM_WEIGHTS[1][598] = -0.146836f;
        SVM_WEIGHTS[1][629] = -0.110386f;
        SVM_WEIGHTS[1][650] = -0.090899f;
        SVM_WEIGHTS[1][663] = -0.168842f;
        SVM_WEIGHTS[1][819] = -0.110386f;
        SVM_WEIGHTS[1][884] = -0.279228f;
        SVM_WEIGHTS[2][1] = -0.163264f;
        SVM_WEIGHTS[2][3] = -0.163201f;
        SVM_WEIGHTS[2][4] = -0.181370f;
        SVM_WEIGHTS[2][62] = 1.000000f;
        SVM_WEIGHTS[2][64] = 1.000000f;
        SVM_WEIGHTS[2][108] = -0.136031f;
        SVM_WEIGHTS[2][110] = -0.636029f;
        SVM_WEIGHTS[2][134] = -0.636029f;
        SVM_WEIGHTS[2][135] = -0.062665f;
        SVM_WEIGHTS[2][154] = -0.028923f;
        SVM_WEIGHTS[2][157] = -0.062664f;
        SVM_WEIGHTS[2][184] = -0.028921f;
        SVM_WEIGHTS[2][185] = -0.098284f;
        SVM_WEIGHTS[2][204] = -0.043385f;
        SVM_WEIGHTS[2][206] = -0.062663f;
        SVM_WEIGHTS[2][223] = -0.108828f;
        SVM_WEIGHTS[2][226] = -0.090684f;
        SVM_WEIGHTS[2][260] = -0.108868f;
        SVM_WEIGHTS[2][261] = -0.108769f;
        SVM_WEIGHTS[2][262] = -0.090687f;
        SVM_WEIGHTS[2][284] = 0.363971f;
        SVM_WEIGHTS[2][286] = 0.363971f;
        SVM_WEIGHTS[2][330] = -0.136031f;
        SVM_WEIGHTS[2][357] = -0.062665f;
        SVM_WEIGHTS[2][376] = -0.057844f;
        SVM_WEIGHTS[2][379] = -0.062664f;
        SVM_WEIGHTS[2][407] = -0.098284f;
        SVM_WEIGHTS[2][426] = -0.043385f;
        SVM_WEIGHTS[2][428] = -0.062663f;
        SVM_WEIGHTS[2][442] = 0.084076f;
        SVM_WEIGHTS[2][444] = -0.062664f;
        SVM_WEIGHTS[2][564] = -0.062665f;
        SVM_WEIGHTS[2][598] = -0.101229f;
        SVM_WEIGHTS[2][629] = -0.098284f;
        SVM_WEIGHTS[2][650] = -0.062663f;
        SVM_WEIGHTS[2][663] = -0.205144f;
        SVM_WEIGHTS[2][819] = -0.098284f;
        SVM_WEIGHTS[2][884] = -0.303428f;
        SVM_WEIGHTS[3][1] = -0.228218f;
        SVM_WEIGHTS[3][3] = -0.228269f;
        SVM_WEIGHTS[3][4] = -0.253604f;
        SVM_WEIGHTS[3][62] = -0.690204f;
        SVM_WEIGHTS[3][64] = -0.126803f;
        SVM_WEIGHTS[3][108] = 0.809797f;
        SVM_WEIGHTS[3][110] = 1.000000f;
        SVM_WEIGHTS[3][134] = -0.126804f;
        SVM_WEIGHTS[3][135] = -0.074835f;
        SVM_WEIGHTS[3][154] = -0.034542f;
        SVM_WEIGHTS[3][157] = -0.074836f;
        SVM_WEIGHTS[3][184] = -0.034535f;
        SVM_WEIGHTS[3][185] = -0.103499f;
        SVM_WEIGHTS[3][204] = -0.051813f;
        SVM_WEIGHTS[3][206] = -0.074836f;
        SVM_WEIGHTS[3][223] = -0.152163f;
        SVM_WEIGHTS[3][226] = -0.126798f;
        SVM_WEIGHTS[3][260] = -0.152116f;
        SVM_WEIGHTS[3][261] = -0.152208f;
        SVM_WEIGHTS[3][262] = -0.126805f;
        SVM_WEIGHTS[3][284] = 0.309796f;
        SVM_WEIGHTS[3][286] = -0.253607f;
        SVM_WEIGHTS[3][330] = 0.809797f;
        SVM_WEIGHTS[3][357] = -0.074835f;
        SVM_WEIGHTS[3][376] = -0.069077f;
        SVM_WEIGHTS[3][379] = -0.074836f;
        SVM_WEIGHTS[3][407] = -0.103499f;
        SVM_WEIGHTS[3][426] = -0.051813f;
        SVM_WEIGHTS[3][428] = -0.074836f;
        SVM_WEIGHTS[3][442] = 0.155895f;
        SVM_WEIGHTS[3][444] = -0.074836f;
        SVM_WEIGHTS[3][564] = -0.074835f;
        SVM_WEIGHTS[3][598] = -0.120891f;
        SVM_WEIGHTS[3][629] = -0.103499f;
        SVM_WEIGHTS[3][650] = -0.074836f;
        SVM_WEIGHTS[3][663] = -0.189503f;
        SVM_WEIGHTS[3][819] = -0.103499f;
        SVM_WEIGHTS[3][884] = -0.293002f;
        SVM_WEIGHTS[4][1] = -0.117415f;
        SVM_WEIGHTS[4][3] = -0.117365f;
        SVM_WEIGHTS[4][4] = -0.130432f;
        SVM_WEIGHTS[4][62] = -0.065209f;
        SVM_WEIGHTS[4][64] = -0.597822f;
        SVM_WEIGHTS[4][108] = -0.097822f;
        SVM_WEIGHTS[4][110] = -0.065217f;
        SVM_WEIGHTS[4][134] = 1.000000f;
        SVM_WEIGHTS[4][135] = 0.537691f;
        SVM_WEIGHTS[4][154] = -0.059524f;
        SVM_WEIGHTS[4][157] = -0.128974f;
        SVM_WEIGHTS[4][184] = -0.059527f;
        SVM_WEIGHTS[4][185] = -0.126703f;
        SVM_WEIGHTS[4][204] = -0.089295f;
        SVM_WEIGHTS[4][206] = -0.128978f;
        SVM_WEIGHTS[4][223] = -0.078260f;
        SVM_WEIGHTS[4][226] = -0.065208f;
        SVM_WEIGHTS[4][260] = -0.078304f;
        SVM_WEIGHTS[4][261] = -0.078216f;
        SVM_WEIGHTS[4][262] = -0.065224f;
        SVM_WEIGHTS[4][284] = -0.130427f;
        SVM_WEIGHTS[4][286] = 0.402178f;
        SVM_WEIGHTS[4][330] = -0.097822f;
        SVM_WEIGHTS[4][357] = 0.537691f;
        SVM_WEIGHTS[4][376] = -0.119051f;
        SVM_WEIGHTS[4][379] = -0.128974f;
        SVM_WEIGHTS[4][407] = -0.126703f;
        SVM_WEIGHTS[4][426] = -0.089295f;
        SVM_WEIGHTS[4][428] = -0.128978f;
        SVM_WEIGHTS[4][442] = -0.191283f;
        SVM_WEIGHTS[4][444] = -0.128974f;
        SVM_WEIGHTS[4][564] = 0.537691f;
        SVM_WEIGHTS[4][598] = -0.208346f;
        SVM_WEIGHTS[4][629] = -0.126703f;
        SVM_WEIGHTS[4][650] = -0.128978f;
        SVM_WEIGHTS[4][663] = -0.119889f;
        SVM_WEIGHTS[4][819] = -0.126703f;
        SVM_WEIGHTS[4][884] = -0.246592f;
        SVM_WEIGHTS[5][1] = -0.056545f;
        SVM_WEIGHTS[5][3] = -0.056491f;
        SVM_WEIGHTS[5][4] = -0.062801f;
        SVM_WEIGHTS[5][62] = -0.031402f;
        SVM_WEIGHTS[5][64] = -0.031398f;
        SVM_WEIGHTS[5][108] = -0.047101f;
        SVM_WEIGHTS[5][110] = -0.031398f;
        SVM_WEIGHTS[5][134] = -0.031402f;
        SVM_WEIGHTS[5][135] = -0.147576f;
        SVM_WEIGHTS[5][154] = 1.000000f;
        SVM_WEIGHTS[5][157] = 0.519086f;
        SVM_WEIGHTS[5][184] = -0.735686f;
        SVM_WEIGHTS[5][185] = -0.134676f;
        SVM_WEIGHTS[5][204] = -0.235683f;
        SVM_WEIGHTS[5][206] = -0.147580f;
        SVM_WEIGHTS[5][223] = -0.037673f;
        SVM_WEIGHTS[5][226] = -0.031402f;
        SVM_WEIGHTS[5][260] = -0.037729f;
        SVM_WEIGHTS[5][261] = -0.037633f;
        SVM_WEIGHTS[5][262] = -0.031399f;
        SVM_WEIGHTS[5][284] = -0.062800f;
        SVM_WEIGHTS[5][286] = -0.062800f;
        SVM_WEIGHTS[5][330] = -0.047101f;
        SVM_WEIGHTS[5][357] = -0.147576f;
        SVM_WEIGHTS[5][376] = 0.264314f;
        SVM_WEIGHTS[5][379] = 0.519086f;
        SVM_WEIGHTS[5][407] = -0.134676f;
        SVM_WEIGHTS[5][426] = -0.235683f;
        SVM_WEIGHTS[5][428] = -0.147580f;
        SVM_WEIGHTS[5][442] = -0.348538f;
        SVM_WEIGHTS[5][444] = 0.519086f;
        SVM_WEIGHTS[5][564] = -0.147576f;
        SVM_WEIGHTS[5][598] = 0.028631f;
        SVM_WEIGHTS[5][629] = -0.134676f;
        SVM_WEIGHTS[5][650] = -0.147580f;
        SVM_WEIGHTS[5][663] = -0.095977f;
        SVM_WEIGHTS[5][819] = -0.134676f;
        SVM_WEIGHTS[5][884] = -0.230653f;
        SVM_WEIGHTS[6][1] = -0.038614f;
        SVM_WEIGHTS[6][3] = -0.038609f;
        SVM_WEIGHTS[6][4] = -0.042903f;
        SVM_WEIGHTS[6][62] = -0.021468f;
        SVM_WEIGHTS[6][64] = -0.021452f;
        SVM_WEIGHTS[6][108] = -0.032179f;
        SVM_WEIGHTS[6][110] = -0.021433f;
        SVM_WEIGHTS[6][134] = -0.021449f;
        SVM_WEIGHTS[6][135] = -0.100820f;
        SVM_WEIGHTS[6][154] = -0.700615f;
        SVM_WEIGHTS[6][157] = -0.100820f;
        SVM_WEIGHTS[6][184] = 1.000000f;
        SVM_WEIGHTS[6][185] = 0.313933f;
        SVM_WEIGHTS[6][204] = -0.200614f;
        SVM_WEIGHTS[6][206] = -0.100820f;
        SVM_WEIGHTS[6][223] = -0.025737f;
        SVM_WEIGHTS[6][226] = -0.021446f;
        SVM_WEIGHTS[6][260] = -0.025742f;
        SVM_WEIGHTS[6][261] = -0.025745f;
        SVM_WEIGHTS[6][262] = -0.021457f;
        SVM_WEIGHTS[6][284] = -0.042900f;
        SVM_WEIGHTS[6][286] = -0.042901f;
        SVM_WEIGHTS[6][330] = -0.032179f;
        SVM_WEIGHTS[6][357] = -0.100820f;
        SVM_WEIGHTS[6][376] = 0.299385f;
        SVM_WEIGHTS[6][379] = -0.100820f;
        SVM_WEIGHTS[6][407] = 0.313933f;
        SVM_WEIGHTS[6][426] = -0.200614f;
        SVM_WEIGHTS[6][428] = -0.100820f;
        SVM_WEIGHTS[6][442] = -0.238107f;
        SVM_WEIGHTS[6][444] = -0.100820f;
        SVM_WEIGHTS[6][564] = -0.100820f;
        SVM_WEIGHTS[6][598] = 0.098771f;
        SVM_WEIGHTS[6][629] = 0.313933f;
        SVM_WEIGHTS[6][650] = -0.100820f;
        SVM_WEIGHTS[6][663] = -0.441797f;
        SVM_WEIGHTS[6][819] = 0.313933f;
        SVM_WEIGHTS[6][884] = -0.127864f;
        SVM_WEIGHTS[7][1] = -0.065582f;
        SVM_WEIGHTS[7][3] = -0.065498f;
        SVM_WEIGHTS[7][4] = -0.072817f;
        SVM_WEIGHTS[7][62] = -0.036413f;
        SVM_WEIGHTS[7][64] = -0.036402f;
        SVM_WEIGHTS[7][108] = -0.054610f;
        SVM_WEIGHTS[7][110] = -0.036405f;
        SVM_WEIGHTS[7][134] = -0.036412f;
        SVM_WEIGHTS[7][135] = -0.171120f;
        SVM_WEIGHTS[7][154] = -0.232829f;
        SVM_WEIGHTS[7][157] = -0.171119f;
        SVM_WEIGHTS[7][184] = -0.232814f;
        SVM_WEIGHTS[7][185] = -0.144765f;
        SVM_WEIGHTS[7][204] = 0.650761f;
        SVM_WEIGHTS[7][206] = 0.495548f;
        SVM_WEIGHTS[7][223] = -0.043692f;
        SVM_WEIGHTS[7][226] = -0.036403f;
        SVM_WEIGHTS[7][260] = -0.043749f;
        SVM_WEIGHTS[7][261] = -0.043639f;
        SVM_WEIGHTS[7][262] = -0.036414f;
        SVM_WEIGHTS[7][284] = -0.072818f;
        SVM_WEIGHTS[7][286] = -0.072814f;
        SVM_WEIGHTS[7][330] = -0.054610f;
        SVM_WEIGHTS[7][357] = -0.171120f;
        SVM_WEIGHTS[7][376] = -0.465643f;
        SVM_WEIGHTS[7][379] = -0.171119f;
        SVM_WEIGHTS[7][407] = -0.144765f;
        SVM_WEIGHTS[7][426] = 0.650761f;
        SVM_WEIGHTS[7][428] = 0.495548f;
        SVM_WEIGHTS[7][442] = -0.404138f;
        SVM_WEIGHTS[7][444] = -0.171119f;
        SVM_WEIGHTS[7][564] = -0.171120f;
        SVM_WEIGHTS[7][598] = 0.185119f;
        SVM_WEIGHTS[7][629] = -0.144765f;
        SVM_WEIGHTS[7][650] = 0.495548f;
        SVM_WEIGHTS[7][663] = -0.065711f;
        SVM_WEIGHTS[7][819] = -0.144765f;
        SVM_WEIGHTS[7][884] = -0.210476f;
    }

// --- GENERATOR BİTTİ ---

}