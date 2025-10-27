package com.example.ml_demo.common;

import android.Manifest;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ml_demo.R;

import org.opencv.android.OpenCVLoader;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class BaseActivity extends Activity {
  private static final String TAG = "活动BaseActivity";
  private static final int PERMISSION_REQUEST_CODE = 100;
  protected Handler mMainHandler;
  public TextView mTitle;
  public LinearLayout llTop;

  public LinearLayout llImage1;
  public TextView mImageTitle1;
  public ImageView mImageView1;
  public Button mImageButton1;

  public LinearLayout llImage2;
  public TextView mImageTitle2;
  public ImageView mImageView2;
  public Button mImageButton2;

  public LinearLayout llMiddle;

  public LinearLayout llImage3;
  public TextView mImageTitle3;
  public ImageView mImageView3;

  public LinearLayout llBottom;
  public FrameLayout mLoadingLayout;
  public TextView mLoadingText;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate");
  }

  protected void initView() {
    mTitle = findViewById(R.id.title);
    llTop = findViewById(R.id.llTop);
    llMiddle = findViewById(R.id.llMiddle);
    llBottom = findViewById(R.id.llBottom);
    llImage1 = findViewById(R.id.llImage1);
    mImageTitle1 = findViewById(R.id.titleImage1);
    mImageView1 = findViewById(R.id.imageView1);
    mImageButton1 = findViewById(R.id.btnImage1);
    llImage2 = findViewById(R.id.llImage2);
    mImageTitle2 = findViewById(R.id.titleImage2);
    mImageView2 = findViewById(R.id.imageView2);
    mImageButton2 = findViewById(R.id.btnImage2);
    llImage3 = findViewById(R.id.llImage3);
    mImageTitle3 = findViewById(R.id.titleImage3);
    mImageView3 = findViewById(R.id.imageView3);
    mLoadingLayout = findViewById(R.id.loadingLayout);
  }

  protected void initVisible() {
    llTop.setVisibility(View.GONE);
    llImage1.setVisibility(View.GONE);
    llImage2.setVisibility(View.GONE);
    llMiddle.setVisibility(View.GONE);
    llImage3.setVisibility(View.GONE);
    llBottom.setVisibility(View.GONE);
    mLoadingLayout.setVisibility(View.GONE);
  }

  protected void initListener() {

  }

  protected void initHandler() {
    mMainHandler = new Handler(getMainLooper()) {
      @Override
      public void handleMessage(@NonNull Message msg) {
        super.handleMessage(msg);
      }
    };
  }

  protected void initOpenCV() {
    if (OpenCVLoader.initLocal()) {
      (Toast.makeText(this, "OpenCV 初始化成功", Toast.LENGTH_LONG)).show();
    } else {
      (Toast.makeText(this, "OpenCV 初始化失败！", Toast.LENGTH_LONG)).show();
    }
  }


  /**
   * 打开图片选择器，选择图片
   *
   * @param requestCode 请求码，用于分辨发起选择的场景
   */
  public void selectImage(int requestCode) {
    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    startActivityForResult(intent, requestCode);
  }


  /**
   * 将位图顺时针旋转指定度数
   *
   * @param bitmap  原本的位图
   * @param degrees 旋转的度数
   * @return 旋转后的位图
   */
  protected Bitmap rotateBitmap(Bitmap bitmap, float degrees) {
    Matrix matrix = new Matrix();
    matrix.postRotate(degrees);
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
  }

  /**
   * 将位图进行水平翻折或竖直翻折
   *
   * @param bitmap     原本的位图
   * @param horizontal 是否水平翻折
   * @param vertical   是否竖直翻折
   * @return 翻折后的位图
   */
  protected Bitmap flipBitmap(Bitmap bitmap, boolean horizontal, boolean vertical) {
    Matrix matrix = new Matrix();
    matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
  }

  /**
   * 根据Exif信息将图片处理成正确的样子
   *
   * @param imageUri 图片uri
   * @return 处理后的位图
   */
  protected Bitmap transformUri2Image(Uri imageUri) {
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
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(selectedBitmap, true, false);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(selectedBitmap, false, true);
            case ExifInterface.ORIENTATION_TRANSPOSE -> flipBitmap(rotateBitmap(selectedBitmap, 90), true, false);
            case ExifInterface.ORIENTATION_TRANSVERSE -> flipBitmap(rotateBitmap(selectedBitmap, 270), true, false);
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

  /**
   * 将一张图片使用指定文件名保存在应用的Files文件内，并返回保存后的绝对路径
   *
   * @param imageUri 图片uri
   * @param filename 文件名
   * @return 保存后文件的绝对路径
   */
  public String saveImageToInternalStorage(Uri imageUri, String filename) {
    try {
      InputStream inputStream = getContentResolver().openInputStream(imageUri);
      File file = new File(getFilesDir(), filename);
      FileOutputStream outputStream = new FileOutputStream(file);

      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, length);
      }

      outputStream.close();
      inputStream.close();

      return file.getAbsolutePath();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * 修改位图大小，并转换为张量
   *
   * @param bitmap      原始位图
   * @param WIDTH_SIZE  目标宽度
   * @param HEIGHT_SIZE 目标长度
   * @return 转换后的张量
   */
  protected Tensor transformImage2Tensor(Bitmap bitmap, int WIDTH_SIZE, int HEIGHT_SIZE) {
    Bitmap resized = Bitmap.createScaledBitmap(bitmap, WIDTH_SIZE, HEIGHT_SIZE, true);

    return TensorImageUtils.bitmapToFloat32Tensor(
        resized,
        TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
        TensorImageUtils.TORCHVISION_NORM_STD_RGB
    );
  }

  protected Bitmap transformTensor2Image(Tensor tensor, int originalWidth, int originalHeight) {
    long[] shape = tensor.shape();
    int WIDTH_SIZE = (int) shape[shape.length - 2];
    int HEIGHT_SIZE = (int) shape[shape.length - 1];
    float[] preds = tensor.getDataAsFloatArray();
    Bitmap mask = Bitmap.createBitmap(WIDTH_SIZE, HEIGHT_SIZE, Bitmap.Config.ARGB_8888);

    for (int y = 0; y < HEIGHT_SIZE; y++) {
      for (int x = 0; x < WIDTH_SIZE; x++) {
        int idx = y * WIDTH_SIZE + x;
        int gray = (int) (preds[idx] * 255);
        int color = Color.rgb(gray, gray, gray);
        mask.setPixel(x, y, color);
      }
    }

    return Bitmap.createScaledBitmap(mask, originalWidth, originalHeight, true);
  }

  /**
   * 将位图保存到当前用用的temp文件夹中，默认使用时间作为文件名保存为png文件，并返回该文件的绝对路径
   *
   * @param bitmap 要保存的位图
   * @return 保存的绝对路径
   */
  protected String saveBitmapToTempFile(Bitmap bitmap) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    String tempName = sdf.format(new Date());
    return saveBitmapToTempFile(bitmap, tempName);
  }

  protected String saveBitmapToTempFile(Bitmap bitmap, String tempName) {
    try {
      File tempFile = new File(getCacheDir(), tempName + ".png");
      FileOutputStream out = new FileOutputStream(tempFile);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
      out.flush();
      out.close();
      return tempFile.getAbsolutePath();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * 在当前应用的asset文件夹中查找指定文件名的文件，并返回该文件的绝对路径
   *
   * @param context   当前环境上下文
   * @param assetName 要查找的文件名
   * @return 该文件的绝对路径
   */
  public String assetFilePath(Context context, String assetName) {
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
   * 检查并自动申请存储权限
   *
   * @return 是否有权限
   */
  public boolean checkAndRequestPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Android 13+ 使用 READ_MEDIA_IMAGES
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_REQUEST_CODE);
        return false;
      }
    } else {
      // Android 13以下使用 READ_EXTERNAL_STORAGE
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        return false;
      }
    }
    return true;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // 权限被授予
        Toast.makeText(this, "存储权限已被授予！", Toast.LENGTH_LONG).show();
      } else {
        // 权限被拒绝
        Toast.makeText(this, "需要授予存储权限！", Toast.LENGTH_LONG).show();
      }
    }
  }

  public void showLoading(String message) {
    if (mLoadingLayout != null) {
      mLoadingLayout.setVisibility(View.VISIBLE);
    }
    if (mLoadingText != null) {
      mLoadingText.setText(message);
    }
  }

  public void hideLoading() {
    if (mLoadingLayout != null) {
      mLoadingLayout.setVisibility(View.GONE);
    }
  }
}
