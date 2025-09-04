package com.example.ml_demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.Nullable;

public class MainActivity extends Activity {
  private static final int PICK_IMAGE_REQUEST = 1;
  
  public final String MODEL_NAME = "u2netp_mobile.ptl";
  public final int WIDTH_SIZE = 320;
  public final int HEIGHT_SIZE = 320;
  
  private Module mModule;
  private Button selectImageButton;
  private ImageView originalImageView;
  private ImageView resultImageView;
  private TextView statusText;
  private LinearLayout resultLayout;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initView();
    
    loadModel();
  }
  
  private void initView() {
    statusText = findViewById(R.id.statusText);
    selectImageButton = findViewById(R.id.selectImageButton);
    originalImageView = findViewById(R.id.originalImageView);
    resultImageView = findViewById(R.id.resultImageView);
    resultLayout = findViewById(R.id.resultLayout);
    
    selectImageButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
      }
    });
  }
  
  private void loadModel() {
    try {
      statusText.setText("正在加载模型...");
      Toast.makeText(this, "正在加载模型...", Toast.LENGTH_SHORT).show();
      String modelPath = assetFilePath(this, MODEL_NAME);
      mModule = LiteModuleLoader.load(modelPath);
      statusText.setText("模型加载完成，点击按钮选择图片");
    } catch (Exception e) {
      statusText.setText("模型加载失败: " + e.getMessage());
      selectImageButton.setEnabled(false);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
      Uri imageUri = data.getData();
      try {
        // 加载选中的图片
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        Bitmap selectedBitmap = BitmapFactory.decodeStream(inputStream);
        
        if (selectedBitmap != null) {
          // 显示原图
          originalImageView.setImageBitmap(selectedBitmap);
          
          // 开始预测
          processImage(selectedBitmap);
        } else {
          Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
        }
      } catch (Exception e) {
        Toast.makeText(this, "图片加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    }
  }
  
  private void processImage(Bitmap bitmap) {
    try {
      statusText.setText("正在进行显著性检测...");
      
      // 预处理
      Tensor inputTensor = transformImage2Tensor(bitmap);
      
      // 模型推理
      Tensor output = mModule.forward(IValue.from(inputTensor)).toTuple()[0].toTensor();
      
      // 后处理
      float[] preds = output.getDataAsFloatArray();
      normalizePredictions(preds);
      
      // 可视化
      Bitmap resultBitmap = createResultBitmap(preds, bitmap.getWidth(), bitmap.getHeight());
      resultImageView.setImageBitmap(resultBitmap);
      resultLayout.setVisibility(View.VISIBLE);
      statusText.setText("显著性检测完成");
    } catch (Exception e) {
      statusText.setText("预测失败: " + e.getMessage());
      Toast.makeText(this, "预测过程出错", Toast.LENGTH_SHORT).show();
    }
  }

  private Tensor transformImage2Tensor(Bitmap bitmap) {
    // 修改图片尺寸为320 * 320(模型原本的输入大小就是320 * 320)
    Bitmap resized = Bitmap.createScaledBitmap(bitmap, WIDTH_SIZE, HEIGHT_SIZE, true);

    // 将图片转换为 Tensor
    return TensorImageUtils.bitmapToFloat32Tensor(
        resized,
        TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
        TensorImageUtils.TORCHVISION_NORM_STD_RGB
    );
  }
  
  private void normalizePredictions(float[] preds) {
    // 找到最小值和最大值
    float min = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;

    for (float v : preds) {
      if (v < min) min = v;
      if (v > max) max = v;
    }

    // 归一化到 [0, 1] 范围
    for (int i = 0; i < preds.length; i++) {
      preds[i] = (preds[i] - min) / (max - min);
    }
  }

  private Bitmap createResultBitmap(float[] preds, int originalWidth, int originalHeight) {
    // 创建320x320的掩码图片
    Bitmap mask = Bitmap.createBitmap(WIDTH_SIZE, HEIGHT_SIZE, Bitmap.Config.ARGB_8888);

    for (int y = 0; y < HEIGHT_SIZE; y++) {
      for (int x = 0; x < WIDTH_SIZE; x++) {
        int idx = y * WIDTH_SIZE + x;
        int gray = (int)(preds[idx] * 255);
        int color = Color.rgb(gray, gray, gray);
        mask.setPixel(x, y, color);
      }
    }

    // 缩放回原图大小
    return Bitmap.createScaledBitmap(mask, originalWidth, originalHeight, true);
  }

  public static String assetFilePath(Context context, String assetName) {
    File file = new File(context.getFilesDir(), assetName);
    if (file.exists() && file.length() > 0) {
      return file.getAbsolutePath();
    }
    try (InputStream is = context.getAssets().open(assetName)) {
      try (OutputStream os = new FileOutputStream(file)) {
        byte[] buffer = new byte[4 * 1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
          os.write(buffer, 0, read);
        }
        os.flush();
      }
      return file.getAbsolutePath();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

}