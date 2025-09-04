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
  public final String MODEL_NAME = "u2netp_mobile.ptl";
  public final String IMG_NAME = "input.png";
  public String MODEL_PATH;
  public String IMG_PATH;
  public final int WIDTH_SIZE = 320;
  public final int HEIGHT_SIZE = 320;
  Module mModule;
  Bitmap mBitmap;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    MODEL_PATH = assetFilePath(this, MODEL_NAME);
    IMG_PATH = assetFilePath(this, IMG_NAME);

    // 加载模型和图片
    mModule = LiteModuleLoader.load(MODEL_PATH);
    mBitmap = BitmapFactory.decodeFile(IMG_PATH);

    Tensor inputTensor = transformImage2Tensor(mBitmap);

    /*
      模型推理
      U^2-Net 为了保留残差信息，模型进行了下采样，一共会有 7 个输出，从 d1~d7。
      d1 综合了所有层的信息，是一个精度最高的输出效果，所以这里我们取 d1。
     */
    Tensor output = mModule.forward(IValue.from(inputTensor)).toTuple()[0].toTensor();

    // 将输出归一化
    float[] preds = output.getDataAsFloatArray();
    float min = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;

    for (float v : preds) {
      if (v < min) min = v;
      if (v > max) max = v;
    }

    for (int i = 0; i < preds.length; i++) {
      preds[i] = (preds[i] - min) / (max - min);
    }

    // 将输出可视化
    Bitmap outputMask = showImage(preds);

    ImageView imageView = new ImageView(this);
    imageView.setImageBitmap(outputMask);
    setContentView(imageView);
  }

  public Tensor transformImage2Tensor(Bitmap bitmap) {
    // 修改图片尺寸为320 * 320(模型原本的输入大小就是320 * 320)
    Bitmap resized = Bitmap.createScaledBitmap(bitmap, WIDTH_SIZE, HEIGHT_SIZE, true);

    // 将图片转换为 Tensor
    return TensorImageUtils.bitmapToFloat32Tensor(
        resized,
        TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
        TensorImageUtils.TORCHVISION_NORM_STD_RGB
    );
  }

  public Bitmap showImage(float[] preds) {
    Bitmap mask = Bitmap.createBitmap(WIDTH_SIZE, HEIGHT_SIZE, Bitmap.Config.ARGB_8888);

    for (int y = 0; y < HEIGHT_SIZE; y++) {
      for (int x = 0; x < WIDTH_SIZE; x++) {
        int idx = y * WIDTH_SIZE + x;
        int gray = (int)(preds[idx] * 255);
        int color = Color.rgb(gray, gray, gray);
        mask.setPixel(x, y, color);
      }
    }

    // 变回原图大小
    return Bitmap.createScaledBitmap(mask, mBitmap.getWidth(), mBitmap.getHeight(), true);
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