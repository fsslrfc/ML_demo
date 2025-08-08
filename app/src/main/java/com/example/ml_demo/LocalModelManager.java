
package com.example.ml_demo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LocalModelManager {
  private static final String TAG = "LocalModelManager";
  private static final String MODEL_NAME = "multimodal_classifier.tflite";

  // 模型输入尺寸
  private static final int IMAGE_INPUT_SIZE = 224;
  private static final int TEXT_INPUT_SIZE = 512;

  private Interpreter interpreter;
  private final Context context;

  // 图像处理器
  private final ImageProcessor imageProcessor;

  public LocalModelManager(Context context) {
    this.context = context;

    // 初始化图像处理器
    imageProcessor = new ImageProcessor.Builder()
        .add(new ResizeOp(IMAGE_INPUT_SIZE, IMAGE_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(new NormalizeOp(0.0f, 255.0f))  // 归一化到[0, 1]
        .build();
  }

  public void loadModel() throws IOException {
    // 加载模型
    MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_NAME);

    Interpreter.Options options = new Interpreter.Options();

    // 修复：使用更兼容的GPU委托初始化方式
    try {
      // 检查是否支持GPU
      CompatibilityList compatList = new CompatibilityList();
      if (compatList.isDelegateSupportedOnThisDevice()) {
        // 使用兼容性更好的方式
        options.setUseNNAPI(true);  // 使用NNAPI作为替代方案
        options.setNumThreads(4);
        Log.d(TAG, "Using NNAPI for acceleration");
      } else {
        // 回退到CPU
        options.setNumThreads(4);
        Log.d(TAG, "Using CPU for inference");
      }
    } catch (Exception e) {
      Log.w(TAG, "GPU acceleration not available, using CPU: " + e.getMessage());
      options.setNumThreads(4);
    }

    interpreter = new Interpreter(modelBuffer, options);
    Log.d(TAG, "Model loaded successfully");
  }

  private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
    AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  public float predict(Bitmap image, String text) {
    if (interpreter == null) {
      Log.e(TAG, "Model not loaded");
      return 0.5f;
    }

    try {
      // 确保图片尺寸正确
      Bitmap resizedImage = Bitmap.createScaledBitmap(image, IMAGE_INPUT_SIZE, IMAGE_INPUT_SIZE, true);

      // 处理图像输入
      TensorImage tensorImage = TensorImage.fromBitmap(resizedImage);
      tensorImage = imageProcessor.process(tensorImage);

      // 准备图像输入数组
      float[][][][] imageInput = new float[1][IMAGE_INPUT_SIZE][IMAGE_INPUT_SIZE][3];
      int[] intValues = new int[IMAGE_INPUT_SIZE * IMAGE_INPUT_SIZE];
      resizedImage.getPixels(intValues, 0, IMAGE_INPUT_SIZE, 0, 0, IMAGE_INPUT_SIZE, IMAGE_INPUT_SIZE);

      // 填充图像数据
      for (int i = 0; i < IMAGE_INPUT_SIZE; i++) {
        for (int j = 0; j < IMAGE_INPUT_SIZE; j++) {
          int val = intValues[i * IMAGE_INPUT_SIZE + j];
          imageInput[0][i][j][0] = ((val >> 16) & 0xFF) / 255.0f;  // R
          imageInput[0][i][j][1] = ((val >> 8) & 0xFF) / 255.0f;   // G
          imageInput[0][i][j][2] = (val & 0xFF) / 255.0f;          // B
        }
      }

      // 处理文本输入
      float[] textInput = processText(text);
      float[][] textInputArray = new float[1][TEXT_INPUT_SIZE];
      System.arraycopy(textInput, 0, textInputArray[0], 0, Math.min(text.length(), TEXT_INPUT_SIZE));

      // 准备输出 - 使用正确的数据类型
      float[][] outputArray = new float[1][1];
      Map<Integer, Object> outputs = new HashMap<>();
      outputs.put(0, outputArray);

      // 准备输入数组 - 确保数据类型正确
      Object[] inputs = new Object[]{imageInput, textInputArray};

      // 运行推理
      interpreter.runForMultipleInputsOutputs(inputs, outputs);

      // 获取结果并确保在有效范围内
      float probability = outputArray[0][0];
      return Math.max(0.0f, Math.min(1.0f, probability));

    } catch (Exception e) {
      Log.e(TAG, "Prediction error: " + e.getMessage(), e);
      return 0.5f;
    }
  }

  private float[] processText(String text) {
    float[] textVector = new float[TEXT_INPUT_SIZE];
    Arrays.fill(textVector, 0.0f);

    // 改进的文本编码方式
    String normalizedText = text.toLowerCase().trim();
    for (int i = 0; i < Math.min(normalizedText.length(), TEXT_INPUT_SIZE); i++) {
      char c = normalizedText.charAt(i);
      // 使用字符的ASCII值进行归一化
      textVector[i] = (float) (c - 'a' + 1) / 26.0f;
      if (textVector[i] < 0 || textVector[i] > 1) {
        textVector[i] = 0.0f;  // 非字母字符设为0
      }
    }

    return textVector;
  }

  public void close() {
    if (interpreter != null) {
      interpreter.close();
      interpreter = null;
    }
  }
}
