package com.example.ml_demo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;

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
  Module mModule;
  String ASSETS_PATH = "";
  String MODEL_NAME = "u2netp_mobile.ptl";
  String IMG_NAME = "input.png";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    String MODEL_PATH = assetFilePath(this, MODEL_NAME);
    mModule = LiteModuleLoader.load(MODEL_PATH);

    String IMG_PATH = assetFilePath(this, IMG_NAME);
    Bitmap bitmap = BitmapFactory.decodeFile(IMG_PATH);
    Bitmap resized = Bitmap.createScaledBitmap(bitmap, 320, 320, true);

    Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
        resized,
        TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
        TensorImageUtils.TORCHVISION_NORM_STD_RGB
    );


    // 推理
    Tensor output = mModule.forward(IValue.from(inputTensor)).toTuple()[0].toTensor();

    float[] scores = output.getDataAsFloatArray();
    // scores 的 shape = (1, 1, 320, 320)，即预测 mask

    float[] preds = output.getDataAsFloatArray();
    float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;

    for (float v : preds) {
      if (v < min) min = v;
      if (v > max) max = v;
    }

    for (int i = 0; i < preds.length; i++) {
      preds[i] = (preds[i] - min) / (max - min);
    }

    int width = 320, height = 320;
    Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int idx = y * width + x;
        int gray = (int)(preds[idx] * 255);
        int color = Color.rgb(gray, gray, gray);
        mask.setPixel(x, y, color);
      }
    }

    // resize 回原图大小
    Bitmap finalMask = Bitmap.createScaledBitmap(mask, bitmap.getWidth(), bitmap.getHeight(), true);

    ImageView imageView = new ImageView(this);
    imageView.setImageBitmap(finalMask);
    setContentView(imageView);
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