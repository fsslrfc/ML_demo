package com.example.ml_demo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
  private static final int PICK_IMAGE_REQUEST = 1;
  private static final int PERMISSION_REQUEST_CODE = 100;

  // 模型文件名
  private static final String U2NET_MODULE = "u2net_mobile.ptl";
  private static final String U2NETP_MODULE = "u2netp_mobile.ptl";
  private static final int RUN_FAIL = 0;
  private static final int LOAD_IMAGE_SUCCESS = 1;
  private static final int MODULE_FORWARD_SUCCESS = 2;
  private static final int SET_IMAGE_SUCCESS = 3;
  private static final int SAVE_IMAGE_SUCCESS = 4;
  public final int WIDTH_SIZE = 320;
  public final int HEIGHT_SIZE = 320;

  private Module mModule;
  private Handler mMainHandler;
  private Button selectImageButton;
  private Button segmentImageButton;
  private Button display3DButton;
  private ImageView originalImageView;
  private ImageView resultImageView;
  private TextView statusText;
  private LinearLayout resultLayout;
  private Spinner modelSpinner;
  private TextView loadingText;
  private LinearLayout loadingLayout;

  private boolean isProcessing = false;

  private String currentModelName = U2NETP_MODULE;
  private Bitmap currentOriginalBitmap;
  private Bitmap currentResultBitmap;
  private float[] currentPredictions;
  private List<String> modelOptions;
  private ArrayAdapter<String> modelAdapter;
  private String TEMP_FILE_PATH;

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
    segmentImageButton = findViewById(R.id.segmentImageButton);
    display3DButton = findViewById(R.id.display3DButton);
    originalImageView = findViewById(R.id.originalImageView);
    resultImageView = findViewById(R.id.resultImageView);
    resultLayout = findViewById(R.id.resultLayout);
    modelSpinner = findViewById(R.id.modelSpinner);
    loadingText = findViewById(R.id.loadingText);
    loadingLayout = findViewById(R.id.loadingLayout);

    setupModelSpinner();

    selectImageButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (checkAndRequestPermissions()) {
          openImagePicker();
        }
      }
    });

    segmentImageButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (currentOriginalBitmap != null) {
          Intent intent = new Intent(MainActivity.this, DisplaySegmentActivity.class);
          intent.putExtra("cropped_image_path", TEMP_FILE_PATH);
          startActivity(intent);
        } else {
          Toast.makeText(MainActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
        }
      }
    });

    display3DButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (currentResultBitmap != null && currentOriginalBitmap != null) {
          ImageDataManager.getInstance().setData(currentOriginalBitmap, TEMP_FILE_PATH);
          Intent intent = new Intent(MainActivity.this, Display3DActivity.class);
          startActivity(intent);
        } else {
          Toast.makeText(MainActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
        }
      }
    });

    mMainHandler = new Handler(getMainLooper()) {
      @Override
      public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
          case LOAD_IMAGE_SUCCESS:
            showLoading("正在模型推理...");
            originalImageView.setImageBitmap((Bitmap) msg.obj);
            break;
          case MODULE_FORWARD_SUCCESS:
            showLoading("正在转换图片...");
            statusText.setText("显著性检测完成" + msg.obj);
            break;
          case SET_IMAGE_SUCCESS:
            showLoading("正在保存结果...");
            resultImageView.setImageBitmap((Bitmap) msg.obj);
            resultLayout.setVisibility(View.VISIBLE);
            segmentImageButton.setVisibility(View.VISIBLE);
            display3DButton.setVisibility(View.VISIBLE);
            break;
          case SAVE_IMAGE_SUCCESS:
            hideLoading();
            break;
          case RUN_FAIL:
            hideLoading();
            Toast.makeText(MainActivity.this, "运行失败: " + msg.obj.toString(), Toast.LENGTH_LONG).show();
            statusText.setText("运行失败: " + msg.obj.toString());
            resultLayout.setVisibility(View.GONE);
            segmentImageButton.setVisibility(View.GONE);
            display3DButton.setVisibility(View.GONE);
            break;
          default:
            break;
        }
      }
    };
  }

  private void setupModelSpinner() {
    modelOptions = new ArrayList<>();
    modelOptions.add("U2NET-P (轻量版 4.7MB)");
    modelOptions.add("U2NET (完整版 173.6MB)");

    modelAdapter = new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_item, modelOptions);
    modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    modelSpinner.setAdapter(modelAdapter);
    modelSpinner.setSelection(0);
    modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String selectedModel = position == 0 ? U2NETP_MODULE : U2NET_MODULE;
        if (!selectedModel.equals(currentModelName)) {
          currentModelName = selectedModel;
          loadModel();
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
    resultLayout.setVisibility(View.GONE);
    segmentImageButton.setVisibility(View.GONE);
    display3DButton.setVisibility(View.GONE);
    currentOriginalBitmap = null;
    currentPredictions = null;
    originalImageView.setImageBitmap(null);
    resultImageView.setImageBitmap(null);
  }

  private void loadModel() {
    try {
      String modelDisplayName = currentModelName.equals(U2NETP_MODULE) ? "U2NET-P" : "U2NET";
      statusText.setText("正在加载" + modelDisplayName + "模型...");
      Toast.makeText(this, "正在加载" + modelDisplayName + "模型...", Toast.LENGTH_SHORT).show();

      String modelPath = assetFilePath(this, currentModelName);
      mModule = LiteModuleLoader.load(modelPath);

      statusText.setText(modelDisplayName + "模型加载完成，点击按钮选择图片");
      selectImageButton.setEnabled(true);
    } catch (Exception e) {
      statusText.setText("模型加载失败: " + e.getMessage());
      selectImageButton.setEnabled(false);
      Toast.makeText(this, "模型加载失败，请检查模型文件", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (!isProcessing && requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
      isProcessing = true;
      showLoading("正在加载图片...");
      Uri imageUri = data.getData();
      if (imageUri != null) {
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              Bitmap rotatedBitmap = rotateImageBasedOnExif(imageUri);
              mMainHandler.sendMessage(Message.obtain(mMainHandler, LOAD_IMAGE_SUCCESS,
                  rotatedBitmap));
              String info = processImage(rotatedBitmap);
              mMainHandler.sendMessage(Message.obtain(mMainHandler, MODULE_FORWARD_SUCCESS, info));
              currentResultBitmap = createResultBitmap(currentPredictions,
                  currentOriginalBitmap.getWidth(), currentOriginalBitmap.getHeight());
              mMainHandler.sendMessage(Message.obtain(mMainHandler, SET_IMAGE_SUCCESS, currentResultBitmap));
              Bitmap croppedBitmap = createCroppedBitmap(currentOriginalBitmap, currentPredictions);
              saveBitmapToTempFile(croppedBitmap);
              mMainHandler.sendMessage(Message.obtain(mMainHandler, SAVE_IMAGE_SUCCESS));
            } catch (Exception e) {
              Log.i("图片加载测试", "图片加载失败！");
              mMainHandler.sendMessage(Message.obtain(mMainHandler, RUN_FAIL, e));
            } finally {
              isProcessing = false;
            }
          }
        }).start();
      } else {
        Toast.makeText(this, "图片获取失败，请重新选择", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private Bitmap rotateImageBasedOnExif(Uri imageUri) {
    try {
      InputStream inputStream = getContentResolver().openInputStream(imageUri);
      Bitmap selectedBitmap = BitmapFactory.decodeStream(inputStream);
      inputStream.close();
      try {
        inputStream = getContentResolver().openInputStream(imageUri);
        if (inputStream != null) {
          ExifInterface exifInterface = new ExifInterface(inputStream);
          int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_UNDEFINED);
          inputStream.close();
          return switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(selectedBitmap, 90);
            case ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(selectedBitmap, 180);
            case ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(selectedBitmap, 270);
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                flipBitmap(selectedBitmap, true, false);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(selectedBitmap, false, true);
            case ExifInterface.ORIENTATION_TRANSPOSE ->
                flipBitmap(rotateBitmap(selectedBitmap, 90), true, false);
            case ExifInterface.ORIENTATION_TRANSVERSE ->
                flipBitmap(rotateBitmap(selectedBitmap, 270), true, false);
            default -> selectedBitmap;
          };
        }
      } catch (Exception e) {
        return selectedBitmap;
      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return null;
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

  /**
   * 模型预测
   *
   * @param bitmap
   */
  private String processImage(Bitmap bitmap) {
    try {
      long startTime = System.currentTimeMillis();

      // 预处理
      Tensor inputTensor = transformImage2Tensor(bitmap);
      long preprocessTime = System.currentTimeMillis() - startTime;

      // 模型推理
      Tensor output = mModule.forward(IValue.from(inputTensor)).toTuple()[0].toTensor();
      long inferenceTime = System.currentTimeMillis() - startTime - preprocessTime;

      // 后处理
      float[] preds = output.getDataAsFloatArray();
      normalizePredictions(preds);
      long postprocessTime =
          System.currentTimeMillis() - startTime - preprocessTime - inferenceTime;

      // 保存当前的原图和预测结果
      currentOriginalBitmap = bitmap;
      currentPredictions = preds.clone();

      return String.format("\n预处理时间: %dms\n推理时间: %dms\n后处理时间: %dms\n", preprocessTime,
          inferenceTime, postprocessTime);
    } catch (Exception e) {
      throw new RuntimeException(e);
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
        int gray = (int) (preds[idx] * 255);
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

    // 创建带透明通道的结果图片，使用高质量配置
    Bitmap croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

    // 直接使用预测数据，避免额外的Bitmap创建和缩放
    // 计算缩放比例
    float scaleX = (float) width / WIDTH_SIZE;
    float scaleY = (float) height / HEIGHT_SIZE;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
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

  private Bitmap createMaskBitmap(float[] predictions) {
    Bitmap mask = Bitmap.createBitmap(WIDTH_SIZE, HEIGHT_SIZE, Bitmap.Config.ARGB_8888);

    for (int y = 0; y < HEIGHT_SIZE; y++) {
      for (int x = 0; x < WIDTH_SIZE; x++) {
        int idx = y * WIDTH_SIZE + x;
        int gray = (int) (predictions[idx] * 255);
        int color = Color.rgb(gray, gray, gray);
        mask.setPixel(x, y, color);
      }
    }

    return mask;
  }

  private void saveBitmapToTempFile(Bitmap bitmap) {
    try {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
      String timestamp = sdf.format(new Date());
      File tempFile = new File(getCacheDir(), timestamp + ".png");
      FileOutputStream out = new FileOutputStream(tempFile);
      // 使用PNG格式和最高质量保存，确保透明度和细节不丢失
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
      out.flush();
      out.close();
      TEMP_FILE_PATH = tempFile.getAbsolutePath();
    } catch (IOException e) {
      e.printStackTrace();
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

  /**
   * 检查和请求权限
   */
  private boolean checkAndRequestPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Android 13+ 使用 READ_MEDIA_IMAGES
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
            PERMISSION_REQUEST_CODE);
        return false;
      }
    } else {
      // Android 13以下使用 READ_EXTERNAL_STORAGE
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, 
            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
            PERMISSION_REQUEST_CODE);
        return false;
      }
    }
    return true;
  }

  /**
   * 打开图片选择器
   */
  private void openImagePicker() {
    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    intent.setType("image/*");
    startActivityForResult(intent, PICK_IMAGE_REQUEST);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    if (requestCode == PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // 权限被授予，打开图片选择器
        openImagePicker();
      } else {
        // 权限被拒绝
        Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_LONG).show();
        
        // 检查是否应该显示权限说明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (ActivityCompat.shouldShowRequestPermissionRationale(this, 
              Manifest.permission.READ_MEDIA_IMAGES)) {
            showPermissionExplanation();
          }
        } else {
          if (ActivityCompat.shouldShowRequestPermissionRationale(this, 
              Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showPermissionExplanation();
          }
        }
      }
    }
  }

  /**
   * 显示权限说明
   */
  private void showPermissionExplanation() {
    Toast.makeText(this, "应用需要访问图片权限来选择和处理图片，请在设置中手动开启权限", 
        Toast.LENGTH_LONG).show();
  }

  /**
   * 显示加载指示器
   */
  private void showLoading(String message) {
    if (loadingLayout != null) {
      loadingLayout.setVisibility(View.VISIBLE);
    }
    if (loadingText != null) {
      loadingText.setText(message);
    }
  }

  /**
   * 隐藏加载指示器
   */
  private void hideLoading() {
    if (loadingLayout != null) {
      loadingLayout.setVisibility(View.GONE);
    }
  }

}