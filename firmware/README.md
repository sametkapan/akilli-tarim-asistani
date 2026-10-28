# ESP32 Firmware

ESP32-S3 üzerinde çalışan sensör istasyonu. Her **5 saniyede** bir sıcaklık, nem ve 3 toprak nem sensörünü okur ve sonuçları MQTT üzerinden yayınlar. Uygulamadan gelen pompa komutlarını dinler.

## Bağlantılar

| Pin | Bileşen | Not |
|---|---|---|
| GPIO 1, 2, 3 | Toprak nem sensörleri 1–3 | Analog okuma (0–4095) |
| GPIO 4 | DHT22 veri | |
| GPIO 16 | DHT22 besleme | Sensör kilitlenirse elektriği kesip yeniden vererek donanımsal reset atılır |
| GPIO 15 | Röle (pompa) | Aktif-düşük: açmak için `LOW`, kapatmak için pin `INPUT` (yüksek empedans) yapılır |

## MQTT

| Kanal | Yön | İçerik |
|---|---|---|
| `tarim/sensorler` | ESP32 → Uygulama | `{"sicaklik": 24.3, "nem": 57, "toprak1": 1830, "toprak2": 1795, "toprak3": 1902}` |
| `tarim/pompa` | Uygulama → ESP32 | `1` aç / `0` kapat |

Broker: `broker.hivemq.com:1883`

## Kurulum

1. Arduino IDE'de **ESP32** kart paketini kurun ve kart olarak **ESP32S3 Dev Module** seçin.
2. Kütüphane yöneticisinden şunları kurun:
   - **PubSubClient** (Nick O'Leary)
   - **ArduinoJson**
   - **DHT sensor library** (Adafruit)
3. `esp32_tarim/secrets.example.h` dosyasını aynı klasöre `secrets.h` adıyla kopyalayın ve Wi-Fi bilgilerinizi girin. `secrets.h` repoya eklenmez.
4. `esp32_tarim/esp32_tarim.ino` dosyasını açıp karta yükleyin. Seri monitörü 115200 baud'da açarak gönderilen verileri izleyebilirsiniz.
