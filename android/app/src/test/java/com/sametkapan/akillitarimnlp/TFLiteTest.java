package com.sametkapan.akillitarimnlp;

import org.junit.Test;
import org.tensorflow.lite.Interpreter;
import java.io.File;
import java.nio.file.Files;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class TFLiteTest {
    @Test
    public void testModel() throws Exception {
        File modelFile = new File("src/main/assets/model.tflite");
        if (!modelFile.exists()) {
            System.out.println("Model not found!");
            return;
        }

        Interpreter tflite = new Interpreter(modelFile);
        
        System.out.println("Input Tensor count: " + tflite.getInputTensorCount());
        System.out.println("Output Tensor count: " + tflite.getOutputTensorCount());
        
        org.tensorflow.lite.Tensor inputTensor = tflite.getInputTensor(0);
        System.out.println("Input shape: " + java.util.Arrays.toString(inputTensor.shape()));
        System.out.println("Input type: " + inputTensor.dataType());
        
        org.tensorflow.lite.Tensor outputTensor = tflite.getOutputTensor(0);
        System.out.println("Output shape: " + java.util.Arrays.toString(outputTensor.shape()));
        System.out.println("Output type: " + outputTensor.dataType());

        // Try float input
        try {
            float[][] inputFloat = new float[1][5];
            float[][] outputFloat = new float[1][8];
            tflite.run(inputFloat, outputFloat);
            System.out.println("Float input worked!");
        } catch (Exception e) {
            System.out.println("Float input failed: " + e.getMessage());
        }

        // Try int input
        try {
            int[][] inputInt = new int[1][5];
            float[][] outputFloat = new float[1][8];
            tflite.run(inputInt, outputFloat);
            System.out.println("Int input worked!");
        } catch (Exception e) {
            System.out.println("Int input failed: " + e.getMessage());
        }
    }
}
