package com.example.ml_demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
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
  private Button show3dImageButton;
  private ImageView originalImageView;
  private ImageView resultImageView;
  private TextView statusText;
  private LinearLayout resultLayout;
  
  // 保存当前处理的图片和预测结果
  private Bitmap currentOriginalBitmap;
  private float[] currentPredictions;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    init();
    loadModel();
  }
  
  private void init() {
    statusText = findViewById(R.id.statusText);
    selectImageButton = findViewById(R.id.selectImageButton);
    show3dImageButton = findViewById(R.id.show3dImageButton);
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
    show3dImageButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (currentOriginalBitmap != null && currentPredictions != null) {
          // 创建裁剪后的图片
          Bitmap croppedBitmap = createCroppedBitmap(currentOriginalBitmap, currentPredictions);
          
          // 跳转到3D展示Activity
          Intent intent = new Intent(MainActivity.this, Display3DActivity.class);
          
          // 将裁剪后的图片保存到临时文件并传递路径
          String imagePath = saveBitmapToTempFile(croppedBitmap);
          intent.putExtra("cropped_image_path", imagePath);
          
          startActivity(intent);
        } else {
          Toast.makeText(MainActivity.this, "请先选择图片并完成检测", Toast.LENGTH_SHORT).show();
        }
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
          // 根据EXIF信息旋转图片
          Bitmap rotatedBitmap = rotateImageBasedOnExif(selectedBitmap, imageUri);
          // 显示原图
          originalImageView.setImageBitmap(rotatedBitmap);
          
          // 开始预测
          processImage(rotatedBitmap);
        } else {
          Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
        }
      } catch (Exception e) {
        Toast.makeText(this, "图片加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    }
  }

  private Bitmap rotateImageBasedOnExif(Bitmap bitmap, Uri imageUri) {
    try {
      // 从URI获取输入流来读取EXIF信息
      InputStream inputStream = getContentResolver().openInputStream(imageUri);
      if (inputStream != null) {
        ExifInterface exifInterface = new ExifInterface(inputStream);
        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        inputStream.close();
        
        switch (orientation) {
          case ExifInterface.ORIENTATION_ROTATE_90:
            return rotateBitmap(bitmap, 90);
          case ExifInterface.ORIENTATION_ROTATE_180:
            return rotateBitmap(bitmap, 180);
          case ExifInterface.ORIENTATION_ROTATE_270:
            return rotateBitmap(bitmap, 270);
          case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
            return flipBitmap(bitmap, true, false);
          case ExifInterface.ORIENTATION_FLIP_VERTICAL:
            return flipBitmap(bitmap, false, true);
          case ExifInterface.ORIENTATION_TRANSPOSE:
            return flipBitmap(rotateBitmap(bitmap, 90), true, false);
          case ExifInterface.ORIENTATION_TRANSVERSE:
            return flipBitmap(rotateBitmap(bitmap, 270), true, false);
          default:
            return bitmap;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return bitmap;
  }
  
  private Bitmap rotateBitmap(Bitmap bitmap, float degrees) {
    Matrix matrix = new Matrix();
    matrix.postRotate(degrees);
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
  }
  
  private Bitmap flipBitmap(Bitmap bitmap, boolean horizontal, boolean vertical) {
    Matrix matrix = new Matrix();
    matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
  }

  private void processImage(Bitmap bitmap) {
    try {
      long startTime = System.currentTimeMillis();

      statusText.setText("正在进行显著性检测...");

      // 预处理
      Tensor inputTensor = transformImage2Tensor(bitmap);
      long preprocessTime = System.currentTimeMillis() - startTime;

      // 模型推理
      Tensor output = mModule.forward(IValue.from(inputTensor)).toTuple()[0].toTensor();
      long inferenceTime = System.currentTimeMillis() - startTime - preprocessTime;

      // 后处理
      float[] preds = output.getDataAsFloatArray();
      normalizePredictions(preds);
      long postprocessTime = System.currentTimeMillis() - startTime - preprocessTime - inferenceTime;
      
      // 保存当前的原图和预测结果
      currentOriginalBitmap = bitmap;
      currentPredictions = preds.clone();
      
      // 可视化
      Bitmap resultBitmap = createResultBitmap(preds, bitmap.getWidth(), bitmap.getHeight());
      resultImageView.setImageBitmap(resultBitmap);
      resultLayout.setVisibility(View.VISIBLE);
      show3dImageButton.setVisibility(View.VISIBLE);
      String timeInfo = String.format("\n预处理时间: %dms\n推理时间: %dms\n后处理时间: %dms\n", preprocessTime, inferenceTime, postprocessTime);
      statusText.setText("显著性检测完成" + timeInfo);
    } catch (Exception e) {
      statusText.setText("预测失败: " + e.getMessage());
      resultLayout.setVisibility(View.GONE);
      show3dImageButton.setVisibility(View.GONE);
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
  
  private Bitmap createCroppedBitmap(Bitmap originalBitmap, float[] predictions) {
    int width = originalBitmap.getWidth();
    int height = originalBitmap.getHeight();
    
    // 创建带透明通道的结果图片
    Bitmap croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    
    // 将预测结果缩放到原图尺寸
    Bitmap scaledMask = Bitmap.createScaledBitmap(
        createMaskBitmap(predictions), width, height, true);
    
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        // 获取原图像素
        int originalPixel = originalBitmap.getPixel(x, y);
        
        // 获取显著性值（0-255）
        int maskPixel = scaledMask.getPixel(x, y);
        int saliency = Color.red(maskPixel); // 灰度图，RGB值相同
        
        // 根据显著性值设置透明度
        // 显著性高的保留，显著性低的变透明
        int alpha = saliency; // 直接使用显著性值作为alpha通道
        
        // 设置新像素（保持原色彩，调整透明度）
        int newPixel = Color.argb(alpha, 
            Color.red(originalPixel), 
            Color.green(originalPixel), 
            Color.blue(originalPixel));
        
        croppedBitmap.setPixel(x, y, newPixel);
      }
    }
    
    return croppedBitmap;
  }
  
  private Bitmap createMaskBitmap(float[] predictions) {
    Bitmap mask = Bitmap.createBitmap(WIDTH_SIZE, HEIGHT_SIZE, Bitmap.Config.ARGB_8888);
    
    for (int y = 0; y < HEIGHT_SIZE; y++) {
      for (int x = 0; x < WIDTH_SIZE; x++) {
        int idx = y * WIDTH_SIZE + x;
        int gray = (int)(predictions[idx] * 255);
        int color = Color.rgb(gray, gray, gray);
        mask.setPixel(x, y, color);
      }
    }
    
    return mask;
  }
  
  private String saveBitmapToTempFile(Bitmap bitmap) {
    try {
      File tempFile = new File(getCacheDir(), "cropped_image_" + System.currentTimeMillis() + ".png");
      FileOutputStream out = new FileOutputStream(tempFile);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
      out.flush();
      out.close();
      return tempFile.getAbsolutePath();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
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