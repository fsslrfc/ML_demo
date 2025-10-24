package com.example.ml_demo.opencv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ml_demo.R;
import com.example.ml_demo.common.BaseActivity;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MainActivity extends BaseActivity {
  private static final String TAG = "活动patchmatch.MainActivity";
  private Button mStartButton;
  private TextView mStatusText;

  private static final int REQUEST_IMAGE_PICK = 1;
  private static final int REQUEST_MASK_PICK = 2;

  private static final int LOAD_IMAGE_SUCCESS = 1;
  private static final int LOAD_MASK_SUCCESS = 2;
  private static final int MODULE_FORWARD_SUCCESS = 3;
  private static final int MODULE_FORWARD_FAIL = 4;

  private String imagePath;
  private String maskPath;
  private String outputPath;
  private Bitmap imageBitmap;
  private Bitmap maskBitmap;
  private Bitmap resultBitmap;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_patchmatch);
    initView();
    initVisible();
    initListener();
    initHandler();
    initOpenCV();
    checkAndRequestPermissions();
    Log.d(TAG, "onCreate");
  }

  @Override
  protected void initView() {
    super.initView();
    mStartButton = findViewById(R.id.startButton);
    mStatusText = findViewById(R.id.statusText);
  }

  @Override
  protected void initVisible() {
    super.initVisible();
    llImage1.setVisibility(View.VISIBLE);
    llImage2.setVisibility(View.VISIBLE);
    llMiddle.setVisibility(View.VISIBLE);
    llBottom.setVisibility(View.VISIBLE);
  }

  @Override
  protected void initListener() {
    super.initListener();
    mImageButton1.setOnClickListener(v -> selectImage(REQUEST_IMAGE_PICK));
    mImageButton2.setOnClickListener(v -> selectImage(REQUEST_MASK_PICK));
    mStartButton.setOnClickListener(v -> processImage());
  }

  protected void initHandler() {
    mMainHandler = new Handler(getMainLooper()) {
      @Override
      public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
          case LOAD_IMAGE_SUCCESS:
            hideLoading();
            mImageView1.setImageBitmap(imageBitmap);
            if (imagePath != null && maskPath != null) {
              mStartButton.setEnabled(true);
            }
            break;
          case LOAD_MASK_SUCCESS:
            hideLoading();
            mImageView2.setImageBitmap(maskBitmap);
            if (imagePath != null && maskPath != null) {
              mStartButton.setEnabled(true);
            }
            break;
          case MODULE_FORWARD_SUCCESS:
            hideLoading();
            mStartButton.setEnabled(true);
            llImage3.setVisibility(View.VISIBLE);
            mImageView3.setImageBitmap(resultBitmap);
            mStatusText.setText((String) msg.obj);
            Toast.makeText(MainActivity.this, "处理完成！", Toast.LENGTH_SHORT).show();
            break;
          case MODULE_FORWARD_FAIL:
            Exception e = (Exception) msg.obj;
            hideLoading();
            mStartButton.setEnabled(true);
            mStatusText.setText("错误: " + e.getMessage());
            Toast.makeText(MainActivity.this, "处理异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
            break;
          default:
            break;
        }
      }
    };
  }

  private void initOpenCV() {
    if (OpenCVLoader.initLocal()) {
      (Toast.makeText(this, "OpenCV 初始化成功", Toast.LENGTH_LONG)).show();
    } else {
      (Toast.makeText(this, "OpenCV 初始化失败！", Toast.LENGTH_LONG)).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && data != null) {
      showLoading("正在加载图像...");
      Uri imageUri = data.getData();
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            if (requestCode == REQUEST_IMAGE_PICK) {
              imagePath = saveImageToInternalStorage(imageUri, "input_image.jpg");
              imageBitmap = BitmapFactory.decodeFile(imagePath);
              mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_IMAGE_SUCCESS));
            } else if (requestCode == REQUEST_MASK_PICK) {
              maskPath = saveImageToInternalStorage(imageUri, "input_mask.jpg");
              maskBitmap = BitmapFactory.decodeFile(maskPath);
              mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_MASK_SUCCESS));
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }).start();
    }
  }

  private void processImage() {
    outputPath = new File(getFilesDir(), "output_image.jpg").getAbsolutePath();

    mStartButton.setEnabled(false);
    showLoading("处理中，请稍候...");

    // 在后台线程执行
    new Thread(() -> {
      try {
        long startTime = System.currentTimeMillis();
        // 调用OpenCV方法
        Mat src = new Mat();
        Mat mask = new Mat();
        Mat result = new Mat();
        Utils.bitmapToMat(BitmapFactory.decodeFile(imagePath), src);
        Utils.bitmapToMat(BitmapFactory.decodeFile(maskPath), mask);

        // 关键修复：转换图像格式
        // 1. 将源图像从RGBA转换为RGB（如果是4通道）
        if (src.channels() == 4) {
          Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB);
        }

        // 2. 将mask转换为单通道灰度图
        if (mask.channels() > 1) {
          Imgproc.cvtColor(mask, mask, Imgproc.COLOR_BGR2GRAY);
        }

        // 3. 确保mask是二值图像（0或255）
        Imgproc.threshold(mask, mask, 127, 255, Imgproc.THRESH_BINARY);

        long preprocessTime = System.currentTimeMillis() - startTime;

        Photo.inpaint(src, mask, result, 3, Photo.INPAINT_TELEA);

        long inferenceTime = System.currentTimeMillis() - startTime - preprocessTime;

        resultBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, resultBitmap);

        long postprocessTime = System.currentTimeMillis() - startTime - preprocessTime - inferenceTime;

        mMainHandler.sendMessage(Message.obtain(mMainHandler, MODULE_FORWARD_SUCCESS, String.format("\n预处理时间: %dms\n推理时间: %dms\n后处理时间: %dms\n", preprocessTime, inferenceTime, postprocessTime)));
      } catch (Exception e) {
        mMainHandler.sendMessage(Message.obtain(mMainHandler, MODULE_FORWARD_FAIL, e));
        e.printStackTrace();
      }
    }).start();
  }
}
