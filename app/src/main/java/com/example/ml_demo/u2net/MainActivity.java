package com.example.ml_demo.u2net;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ml_demo.R;
import com.example.ml_demo.common.BaseActivity;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class MainActivity extends BaseActivity {
  private static final String TAG = "活动u2net.MainActivity";
  private static final int PICK_IMAGE_REQUEST = 1;

  // 模型文件名
  private static final String U2NET_MODULE = "u2net_mobile.ptl";
  private static final String U2NETP_MODULE = "u2netp_mobile.ptl";
  private static final String U2NET_NOTE = "U2NET (完整版 173.6MB)";
  private static final String U2NETP_NOTE = "U2NET-P (轻量版 4.7MB)";
  private static final int RUN_FAIL = 0;
  private static final int LOAD_MODULE_SUCCESS = 1;
  private static final int LOAD_MODULE_FAILED = 2;
  private static final int LOAD_IMAGE_SUCCESS = 3;
  private static final int MODULE_FORWARD_SUCCESS = 4;
  private static final int SET_IMAGE_SUCCESS = 5;
  private static final int OPENCV_FORWARD_SUCCESS = 6;
  private static final int OPENCV_FORWARD_FAIL = 7;
  private static final int SAVE_IMAGE_SUCCESS = 8;
  public final int WIDTH_SIZE = 320;
  public final int HEIGHT_SIZE = 320;
  public final float LEVEL = 0.1f;

  private Module mModule;
  private Button segmentImageButton;
  private Button display3DButton;
  private TextView statusText;
  private Spinner modelSpinner;


  private String currentModelName = U2NETP_MODULE;
  private Bitmap currentOriginalBitmap;
  private Bitmap currentResultBitmap;
  private float[] currentPredictions;
  private Bitmap currentMaskBitmap;
  private Bitmap currentCroppedBitmap;
  private int originalWidth;
  private int originalHeight;
  private boolean isSaving = false;
  private List<String> modelOptions;
  private ArrayAdapter<String> modelAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_u2net);
    initView();
    initVisible();
    initListener();
    initHandler();
    initOpenCV();
    Log.d(TAG, "onCreate");
  }

  @Override
  protected void initView() {
    super.initView();
    statusText = findViewById(R.id.statusText);
    segmentImageButton = findViewById(R.id.segmentImageButton);
    display3DButton = findViewById(R.id.display3DButton);
    modelSpinner = findViewById(R.id.modelSpinner);
    mLoadingText = findViewById(R.id.loadingText);
    mLoadingLayout = findViewById(R.id.loadingLayout);
  }

  @Override
  protected void initVisible() {
    super.initVisible();
    llTop.setVisibility(View.VISIBLE);
    llImage1.setVisibility(View.VISIBLE);
    llMiddle.setVisibility(View.VISIBLE);
    llBottom.setVisibility(View.VISIBLE);
    setupModelSpinner();
  }

  @Override
  protected void initListener() {
    super.initListener();
    setupModelSpinner();
    mImageButton1.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (checkAndRequestPermissions()) {
          selectImage(PICK_IMAGE_REQUEST);
        }
      }
    });
    segmentImageButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (isSaving) {
          Toast.makeText(MainActivity.this, "正在保存结果，请稍后", Toast.LENGTH_SHORT).show();
          return;
        }
        if (currentOriginalBitmap != null && currentResultBitmap != null && currentMaskBitmap != null && currentCroppedBitmap != null) {
          ImageDataManager.getInstance().setData(currentOriginalBitmap, currentResultBitmap, currentMaskBitmap, currentCroppedBitmap);
          Intent intent = new Intent(MainActivity.this, DisplaySegmentActivity.class);
          startActivity(intent);
        } else {
          Toast.makeText(MainActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
        }
      }
    });

    display3DButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (isSaving) {
          Toast.makeText(MainActivity.this, "正在保存结果，请稍后", Toast.LENGTH_SHORT).show();
          return;
        }
        if (currentOriginalBitmap != null && currentResultBitmap != null && currentMaskBitmap != null && currentCroppedBitmap != null) {
          ImageDataManager.getInstance().setData(currentOriginalBitmap, currentResultBitmap, currentMaskBitmap, currentCroppedBitmap);
          Intent intent = new Intent(MainActivity.this, Display3dActivity.class);
          startActivity(intent);
        } else {
          Toast.makeText(MainActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
        }
      }
    });
  }

  @Override
  protected void initHandler() {
    mMainHandler = new Handler(getMainLooper()) {
      @Override
      public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
          case LOAD_MODULE_SUCCESS:
            statusText.setText("模型加载完成，点击按钮选择图片");
            mImageButton1.setEnabled(true);
            hideLoading();
            break;
          case LOAD_MODULE_FAILED:
            statusText.setText("模型加载失败！");
            mImageButton1.setEnabled(false);
            Toast.makeText(MainActivity.this, "模型加载失败，请检查模型文件", Toast.LENGTH_SHORT).show();
            break;
          case LOAD_IMAGE_SUCCESS:
            showLoading("正在模型推理...");
            mImageView1.setImageBitmap(currentOriginalBitmap);
            break;
          case MODULE_FORWARD_SUCCESS:
            showLoading("正在保存结果...");
            break;
          case SET_IMAGE_SUCCESS:
            showLoading("正在综合处理...");
            mImageView3.setImageBitmap(currentResultBitmap);
            llImage3.setVisibility(View.VISIBLE);
            llMiddle.setVisibility(View.VISIBLE);
            statusText.setText("显著性检测完成" + msg.obj.toString());
            break;
          case OPENCV_FORWARD_SUCCESS:
          case SAVE_IMAGE_SUCCESS:
            hideLoading();
            break;
          case RUN_FAIL:
          case OPENCV_FORWARD_FAIL:
            hideLoading();
            Toast.makeText(MainActivity.this, "运行失败: " + msg.obj.toString(), Toast.LENGTH_LONG).show();
            statusText.setText("运行失败: " + msg.obj.toString());
            llImage3.setVisibility(View.GONE);
            llMiddle.setVisibility(View.GONE);
            break;
          default:
            break;
        }
      }
    };
  }

  private void setupModelSpinner() {
    modelOptions = new ArrayList<>();
    modelOptions.add(U2NETP_NOTE);
    modelOptions.add(U2NET_NOTE);

    modelAdapter = new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_item, modelOptions);
    modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    modelSpinner.setAdapter(modelAdapter);
    modelSpinner.setSelection(1);
    modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String selectedModel = position == 0 ? U2NETP_MODULE : U2NET_MODULE;
        if (!selectedModel.equals(currentModelName)) {
          currentModelName = selectedModel;
          showLoading("正在加载模型...");
          loadModule();
          mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_MODULE_SUCCESS));
          clearResults();
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // 不做任何操作
      }
    });
  }

  private void clearResults() {
    llImage3.setVisibility(View.GONE);
    llMiddle.setVisibility(View.GONE);
    currentOriginalBitmap = null;
    currentPredictions = null;
    mImageView1.setImageBitmap(null);
    mImageView3.setImageBitmap(null);
  }

  private void loadModule() {
    String modelDisplayName = currentModelName.equals(U2NETP_MODULE) ? "U2NET-P" : "U2NET";
    statusText.setText("正在加载" + modelDisplayName + "模型...");
    String modelPath = assetFilePath(this, currentModelName);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          mModule = LiteModuleLoader.load(modelPath);
          mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_MODULE_SUCCESS));
        } catch (Exception e) {
          mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_MODULE_FAILED));
        }
      }
    }).start();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
      showLoading("正在加载图片...");
      Uri imageUri = data.getData();
      if (imageUri != null) {
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              // 处理图像
              u2netProcessImage(imageUri);
              opencvProcessImage();
              isSaving = true;
              saveBitmapToTempFile(currentOriginalBitmap, "u2net_original");
              saveBitmapToTempFile(currentResultBitmap, "u2net_result");
              saveBitmapToTempFile(currentMaskBitmap, "u2net_mask");
              saveBitmapToTempFile(currentCroppedBitmap, "u2net_cropped");
              isSaving = false;
              mMainHandler.sendMessage(Message.obtain(mMainHandler, SAVE_IMAGE_SUCCESS));
            } catch (Exception e) {
              Log.i("图片加载测试", "图片加载失败！");
              mMainHandler.sendMessage(Message.obtain(mMainHandler, RUN_FAIL, e));
            }
          }
        }).start();
      } else {
        Toast.makeText(this, "图片获取失败，请重新选择", Toast.LENGTH_SHORT).show();
      }
    }
  }

  /**
   * U2-Net模型预测
   *
   * @param imageUri 图片uri
   */
  private void u2netProcessImage(Uri imageUri) {
    try {
      long startTime = System.currentTimeMillis();

      // 预处理：uri -> bitmap -> tensor
      Bitmap originalBitmap = transformUri2Image(imageUri);
      currentOriginalBitmap = originalBitmap;
      mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_IMAGE_SUCCESS));
      originalWidth = originalBitmap.getWidth();
      originalHeight = originalBitmap.getHeight();
      Tensor inputTensor = transformImage2Tensor(originalBitmap, WIDTH_SIZE, HEIGHT_SIZE);
      long preProcessTime = System.currentTimeMillis() - startTime;

      // 模型推理：tensor -> tensor
      Tensor output = mModule.forward(IValue.from(inputTensor)).toTuple()[0].toTensor();
      mMainHandler.sendMessage(Message.obtain(mMainHandler, MODULE_FORWARD_SUCCESS));
      long inferenceTime = System.currentTimeMillis() - startTime - preProcessTime;

      // 后处理：tensor -> float[] -> bitmap
      float[] preds = output.getDataAsFloatArray();
      currentPredictions = normalizePredictions(preds);
      currentResultBitmap = transformTensor2Image(output, originalWidth, originalHeight);
      currentCroppedBitmap = createCroppedBitmap(currentOriginalBitmap, currentPredictions);
      currentMaskBitmap = normalizeMask(currentPredictions, LEVEL);
      long postProcessTime = System.currentTimeMillis() - startTime - preProcessTime - inferenceTime;

      // 耗时统计
      String info = String.format("\n预处理时间: %dms\n推理时间: %dms\n后处理时间: %dms\n", preProcessTime, inferenceTime, postProcessTime);
      mMainHandler.sendMessage(Message.obtain(mMainHandler, SET_IMAGE_SUCCESS, info));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void opencvProcessImage(){
    new Thread(() -> {
      try {
        long startTime = System.currentTimeMillis();
        // 调用OpenCV方法
        Mat src = new Mat();
        Mat mask = new Mat();
        Mat result = new Mat();
        Utils.bitmapToMat(currentOriginalBitmap, src);
        Utils.bitmapToMat(currentMaskBitmap, mask);

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

        long preProcessTime = System.currentTimeMillis() - startTime;

        // OpenCV修复
        Photo.inpaint(src, mask, result, 3, Photo.INPAINT_TELEA);

        long inferenceTime = System.currentTimeMillis() - startTime - preProcessTime;

        currentResultBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, currentResultBitmap);

        long postProcessTime = System.currentTimeMillis() - startTime - preProcessTime - inferenceTime;
        String info = String.format("\n预处理时间: %dms\n推理时间: %dms\n后处理时间: %dms\n", preProcessTime, inferenceTime, postProcessTime);
        mMainHandler.sendMessage(Message.obtain(mMainHandler, OPENCV_FORWARD_SUCCESS, info));
      } catch (Exception e) {
        mMainHandler.sendMessage(Message.obtain(mMainHandler, OPENCV_FORWARD_FAIL, e));
        e.printStackTrace();
      }
    }).start();
  }

  private float[] normalizePredictions(float[] preds) {
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

    return preds;
  }

  private Bitmap normalizeMask(float[] preds, float level) {
    // 归一化到 (0, 1) 二值
    for (int i = 0; i < preds.length; i++) {
      preds[i] = preds[i] >= level ? 1f : 0f;
    }

    return transformFloatArray2Image(preds, WIDTH_SIZE, HEIGHT_SIZE, originalWidth, originalHeight);
  }

  private Bitmap createCroppedBitmap(Bitmap originalBitmap, float[] predictions) {

    // 创建带透明通道的结果图片，使用高质量配置
    Bitmap croppedBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888);

    // 直接使用预测数据，避免额外的Bitmap创建和缩放
    // 计算缩放比例
    float scaleX = (float) originalWidth / WIDTH_SIZE;
    float scaleY = (float) originalHeight / HEIGHT_SIZE;

    for (int y = 0; y < originalHeight; y++) {
      for (int x = 0; x < originalWidth; x++) {
        // 获取原图像素
        int originalPixel = originalBitmap.getPixel(x, y);

        // 计算在预测数组中的对应位置（使用双线性插值获得更好的质量）
        float predX = x / scaleX;
        float predY = y / scaleY;

        // 边界检查
        int x1 = Math.max(0, Math.min(WIDTH_SIZE - 1, (int) predX));
        int y1 = Math.max(0, Math.min(HEIGHT_SIZE - 1, (int) predY));
        int x2 = Math.max(0, Math.min(WIDTH_SIZE - 1, x1 + 1));
        int y2 = Math.max(0, Math.min(HEIGHT_SIZE - 1, y1 + 1));

        // 双线性插值获取更精确的显著性值
        float fx = predX - x1;
        float fy = predY - y1;

        float pred1 = predictions[y1 * WIDTH_SIZE + x1];
        float pred2 = predictions[y1 * WIDTH_SIZE + x2];
        float pred3 = predictions[y2 * WIDTH_SIZE + x1];
        float pred4 = predictions[y2 * WIDTH_SIZE + x2];

        float interpolatedPred = pred1 * (1 - fx) * (1 - fy) +
            pred2 * fx * (1 - fy) +
            pred3 * (1 - fx) * fy +
            pred4 * fx * fy;

        // 将显著性值转换为alpha通道
        int alpha = Math.max(0, Math.min(255, (int) (interpolatedPred * 255)));

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

}