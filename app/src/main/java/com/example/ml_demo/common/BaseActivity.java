package com.example.ml_demo.common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

  public void selectImage(int requestCode) {
    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    startActivityForResult(intent, requestCode);
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

  public String saveImageToInternalStorage(Uri imageUri, String filename) throws IOException {
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
  }

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
}
