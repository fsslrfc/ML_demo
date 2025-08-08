
package com.example.ml_demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

  private static final int PICK_IMAGE_REQUEST = 1;
  private static final int PERMISSION_REQUEST_CODE = 100;

  private ImageView imageView;
  private EditText editText;
  private Button btnSelectImage, btnPredict;
  private TextView textResult;
  private ProgressBar progressBar;

  private Bitmap selectedBitmap;
  private LocalModelManager modelManager;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // 初始化视图
    initViews();

    // 初始化模型管理器
    modelManager = new LocalModelManager(this);

    // 加载模型
    loadModel();

    // 检查权限
    checkPermissions();

    // 设置点击事件
    setupClickListeners();
  }

  private void initViews() {
    imageView = findViewById(R.id.imageView);
    editText = findViewById(R.id.editText);
    btnSelectImage = findViewById(R.id.btnSelectImage);
    btnPredict = findViewById(R.id.btnPredict);
    textResult = findViewById(R.id.textResult);
    progressBar = findViewById(R.id.progressBar);
  }

  private void loadModel() {
    try {
      modelManager.loadModel();
      Toast.makeText(this, "模型加载成功", Toast.LENGTH_SHORT).show();
    } catch (IOException e) {
      e.printStackTrace();
      Toast.makeText(this, "模型加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void checkPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
          new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
          PERMISSION_REQUEST_CODE);
    }
  }

  private void setupClickListeners() {
    btnSelectImage.setOnClickListener(v -> showImagePickerDialog());
    btnPredict.setOnClickListener(v -> predict());
  }

  private void showImagePickerDialog() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.setAction(Intent.ACTION_GET_CONTENT);
    startActivityForResult(Intent.createChooser(intent, "选择图片"), PICK_IMAGE_REQUEST);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
      Uri imageUri = data.getData();
      try {
        selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
        // 调整图片大小
        selectedBitmap = Bitmap.createScaledBitmap(selectedBitmap, 224, 224, true);
        imageView.setImageBitmap(selectedBitmap);
      } catch (IOException e) {
        e.printStackTrace();
        Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private void predict() {
    if (selectedBitmap == null) {
      Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
      return;
    }

    String text = editText.getText().toString().trim();
    if (text.isEmpty()) {
      Toast.makeText(this, "请输入文本描述", Toast.LENGTH_SHORT).show();
      return;
    }

    // 显示进度条
    progressBar.setVisibility(View.VISIBLE);
    btnPredict.setEnabled(false);

    // 在后台线程中运行推理
    new Thread(() -> {
      float probability = modelManager.predict(selectedBitmap, text);
      int prediction = probability >= 0.5f ? 1 : 0;

      runOnUiThread(() -> {
        showResult(probability, prediction);
      });
    }).start();
  }

  private void showResult(float probability, int prediction) {
    progressBar.setVisibility(View.GONE);
    btnPredict.setEnabled(true);

    String resultText = String.format(
        "本地预测结果: %d\n置信度: %.2f%%",
        prediction,
        probability * 100
    );
    textResult.setText(resultText);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                         int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (modelManager != null) {
      modelManager.close();
    }
  }
}
