#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "DHT.h"
#include "secrets.h"   // WIFI_SSID, WIFI_PASSWORD (repoya eklenmez, bkz. secrets.example.h)

// --- Ayarlar ---
const char* ssid = WIFI_SSID;
const char* password = WIFI_PASSWORD;
const char* mqtt_server = "broker.hivemq.com"; 

// Pin Tanımlamaları
const int nemPin1 = 1;  
const int nemPin2 = 2;  
const int nemPin3 = 3;  
const int dhtPin  = 4;  
const int rolePin = 15; 
const int dhtPowerPin = 16; // DHT22'nin elektriğini buradan vereceğiz

#define DHTTYPE DHT22
DHT dht(dhtPin, DHTTYPE);

WiFiClient espClient;
PubSubClient client(espClient);
unsigned long sonMesaj = 0;

// Sensörü elektriği kesip açarak resetleyen fonksiyon
void dhtHardReset() {
  Serial.println("!!! DHT22 Kilitlendi. Donanimsal Reset Atiliyor...");
  digitalWrite(dhtPowerPin, LOW); // Elektriği kes
  delay(2000);                    // Kondansatörlerin boşalması için bekle
  digitalWrite(dhtPowerPin, HIGH); // Elektriği geri ver
  delay(2000);                    // Sensörün açılış süresi
  dht.begin();                    // Kütüphaneyi yeniden başlat
  Serial.println("!!! DHT22 Yeniden Baslatildi.");
}

void setup_wifi() {
  delay(10);
  Serial.println("Wi-Fi baglaniyor...");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi baglandi.");
}

void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (int i = 0; i < length; i++) message += (char)payload[i];
  
  if (String(topic) == "tarim/pompa") {
    if (message == "1") {
      pinMode(rolePin, OUTPUT);
      digitalWrite(rolePin, LOW); 
      Serial.println("Pompa ACILDI");
    } else {
      pinMode(rolePin, INPUT); 
      Serial.println("Pompa KAPATILDI");
    }
  }
}

void reconnect() {
  while (!client.connected()) {
    if (client.connect("ESP32S3_Tarim_Client")) {
      client.subscribe("tarim/pompa");
    } else {
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  
  // DHT Güç pinini ayarla
  pinMode(dhtPowerPin, OUTPUT);
  digitalWrite(dhtPowerPin, HIGH); // Sensöre ilk elektriği ver
  
  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
  
  dht.begin();
  pinMode(rolePin, INPUT); 
  delay(2000);
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();

  unsigned long simdi = millis();
  if (simdi - sonMesaj > 5000) {
    sonMesaj = simdi;

    // Verileri oku
    float h = dht.readHumidity();
    float t = dht.readTemperature();

    // Eğer veri bozulmuşsa donanımsal reset at
    if (isnan(t) || isnan(h)) {
      dhtHardReset();
      h = dht.readHumidity(); // Reset sonrası tekrar oku
      t = dht.readTemperature();
    }

    int n1 = analogRead(nemPin1);
    int n2 = analogRead(nemPin2);
    int n3 = analogRead(nemPin3);

    // JSON Hazırla
    StaticJsonDocument<200> doc;
    doc["sicaklik"] = isnan(t) ? 0 : t;
    doc["nem"] = isnan(h) ? 0 : h;
    doc["toprak1"] = n1;
    doc["toprak2"] = n2;
    doc["toprak3"] = n3;

    char buffer[200];
    serializeJson(doc, buffer);
    client.publish("tarim/sensorler", buffer);
    Serial.println("Veri gonderildi: " + String(buffer));
  }
}
