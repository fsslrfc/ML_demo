package com.example.ml_demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;

public class DisplaySegmentActivity extends Activity {

  private ImageView croppedImageView;
  private Button clearButton;
  private Spinner historySpinner;
  private List<String> historyImagePaths;
  private List<String> historyImageOptions;
  private ArrayAdapter<String> historyAdapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_display_segment);

    init();
    loadCroppedImage();
  }

  private void init() {
    croppedImageView = findViewById(R.id.croppedImageView);
    clearButton = findViewById(R.id.clearButton);
    historySpinner = findViewById(R.id.historySpinner);

    setupHistorySpinner();

    clearButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        try {
          File cacheDir = getCacheDir();
          if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
              for (File file : files) {
                file.delete();
              }
            }
          }
        } catch (Exception e) {
          Log.e("清空缓存日志", "清空缓存失败: " + e.getMessage());
          throw new RuntimeException(e);
        }
        finish();
        Toast.makeText(DisplaySegmentActivity.this, "历史图片已清空！", Toast.LENGTH_LONG).show();
      }
    });
  }

  private void setupHistorySpinner() {
    historyImagePaths = new ArrayList<>();
    loadHistoryImages();
    historyImageOptions = new ArrayList<>();
    for (int i = 0; i < historyImagePaths.size(); i++) {
      historyImageOptions.add(historyImagePaths.get(i).split("/")[historyImagePaths.get(i).split("/").length - 1]);
    }

    historyAdapter = new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_item, historyImageOptions);
    historyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    historySpinner.setAdapter(historyAdapter);

    historySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position > 0 && position <= historyImagePaths.size()) {
          loadHistoryImage(historyImagePaths.get(position));
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // 不做任何操作
      }
    });
  }

  private void loadHistoryImages() {
    historyImagePaths.clear();
    File cacheDir = getCacheDir();
    File[] files = cacheDir.listFiles();

    if (files != null) {
      List<File> imageFiles = new ArrayList<>();
      historyImagePaths.add("请选择历史图片...");
      Collections.addAll(imageFiles, files);
      for (int i = 0; i < imageFiles.size(); i++) {
        historyImagePaths.add(imageFiles.get(i).getAbsolutePath());
        Log.d("保存图片日志", "历史图片路径: " + historyImagePaths.get(i));
      }
    }
  }

  private void loadHistoryImage(String imagePath) {
    try {
      Bitmap historyBitmap = BitmapFactory.decodeFile(imagePath);
      if (historyBitmap != null) {
        // 显示历史图片到原图位置
        croppedImageView.setImageBitmap(historyBitmap);
        Toast.makeText(this, "历史图片加载成功", Toast.LENGTH_SHORT).show();
      } else {
        Toast.makeText(this, "无法加载历史图片", Toast.LENGTH_SHORT).show();
      }
    } catch (Exception e) {
      Toast.makeText(this, "加载历史图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void loadCroppedImage() {
    String imagePath = getIntent().getStringExtra("cropped_image_path");

    if (imagePath != null) {
      Bitmap croppedBitmap = BitmapFactory.decodeFile(imagePath);
      if (croppedBitmap != null) {
        croppedImageView.setImageBitmap(croppedBitmap);
      }
    }
  }
}