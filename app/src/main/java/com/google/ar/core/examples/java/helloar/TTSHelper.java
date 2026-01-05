package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TTSHelper {

    private TextToSpeech tts;
    private long lastAudioTime = 0;
    private static final long COOLDOWN_MS = 5000;

    private String currentLanguage = "en"; // en or fil
    private Map<String, Integer> labelToIndex;
    private Map<String, String> filipinoLabels;
    public TTSHelper(Context context) {
        // 1. Initialize label map
        labelToIndex = new HashMap<>();
        labelToIndex.put("Sink", 0);
        labelToIndex.put("Traffic light", 1);
        labelToIndex.put("Bicycle", 2);
        labelToIndex.put("Bus", 3);
        labelToIndex.put("Person", 4);
        labelToIndex.put("Chair", 5);
        labelToIndex.put("Couch", 6);
        labelToIndex.put("Door", 7);
        labelToIndex.put("Street light", 8);
        labelToIndex.put("Bed", 9);
        labelToIndex.put("Refrigerator", 10);
        labelToIndex.put("Motorcycle", 11);
        labelToIndex.put("Table", 12);
        labelToIndex.put("Television", 13);
        labelToIndex.put("Truck", 14);
        labelToIndex.put("Toilet", 15);
        labelToIndex.put("Bench", 16);
        labelToIndex.put("Car", 17);
        labelToIndex.put("Stairs", 18);
        // Add all your labels here with a unique index
        filipinoLabels = new HashMap<>();
        filipinoLabels.put("Sink", "lababo");
        filipinoLabels.put("Traffic light","Ilaw Trapiko");
        filipinoLabels.put("Bicycle","Bisikleta");
        filipinoLabels.put("Bus", "Bus");
        filipinoLabels.put("Person", "Tao");
        filipinoLabels.put("Chair", "Silya");
        filipinoLabels.put("Couch", "Sopa");
        filipinoLabels.put("Door", "Pinto");
        filipinoLabels.put("Street light", "Poste ng Ilaw");
        filipinoLabels.put("Bed", "Kama");
        filipinoLabels.put("Refrigerator", "Refrigerator");
        filipinoLabels.put("Motorcycle","Motorsiklo");
        filipinoLabels.put("Table", "Mesa");
        filipinoLabels.put("Television", "Telebisyon");
        filipinoLabels.put("Truck", "Truck");
        filipinoLabels.put("Toilet", "Kubeta");
        filipinoLabels.put("Bench", "Bangko");
        filipinoLabels.put("Car", "Kotse");
        filipinoLabels.put("Stairs", "Hagdan");
        // 2. Init TTS
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                Log.d("TTSHelper", "TTS initialized");
            } else {
                Log.e("TTSHelper", "TTS initialization failed");
            }
        });
    }

    public void speak(String text) {
        long now = System.currentTimeMillis();
        if (now - lastAudioTime >= COOLDOWN_MS) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SPEAK_CALL");
            lastAudioTime = now;
        }
    }

    /** Convert label to index */
    public int getClassIndex(String label) {
        if (labelToIndex.containsKey(label)) {
            return labelToIndex.get(label);
        }
        return -1;
    }
    private String getLocalizedLabel(String label) {
        if (currentLanguage.equals("fil") && filipinoLabels.containsKey(label)) {
            return filipinoLabels.get(label);
        }
        return label; // fallback to English
    }

    private String formatDistance(float distance) {
        if (currentLanguage.equals("en")) {
            return String.format("%.1f meters away", distance);
        } else {
            return String.format("%.1f metro ang layo", distance);
        }
    }

    private String formatSentence(String label, float distance, YoloDetector.Direction direction) {
        String localizedLabel = getLocalizedLabel(label);

        // Determine direction text
        String directionText = "";
        if (currentLanguage.equals("en")) {
            switch (direction) {
                case LEFT:
                    directionText = "on your left";
                    break;
                case CENTER:
                    directionText = "in front of you";
                    break;
                case RIGHT:
                    directionText = "on your right";
                    break;
            }
        } else { // Filipino
            switch (direction) {
                case LEFT:
                    directionText = "sa kaliwa mo";
                    break;
                case CENTER:
                    directionText = "sa harap mo";
                    break;
                case RIGHT:
                    directionText = "sa kanan mo";
                    break;
            }
        }

        if (currentLanguage.equals("en")) {
            return localizedLabel + " detected, " + String.format("%.1f meters away", distance) + " " + directionText;
        } else {
            return "Mayroong " + localizedLabel + ", " + String.format("%.1f metro ang layo", distance) + " " + directionText;
        }
    }


    /** Speak an object detection with distance, localized */
    public void speakDetection(String label, float distance, YoloDetector.Direction direction) {
        speak(formatSentence(label, distance, direction));
    }

    /** Change TTS language */
    public void setLanguage(String langCode) {
        currentLanguage = langCode;
        if (langCode.equals("en")) tts.setLanguage(Locale.US);
        else if (langCode.equals("fil")) tts.setLanguage(new Locale("fil", "PH"));
    }

    public void shutdown() {
        if (tts != null) tts.shutdown();
    }
}
