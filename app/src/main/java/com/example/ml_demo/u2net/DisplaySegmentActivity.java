package com.example.ml_demo.u2net;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

import com.example.ml_demo.R;

import androidx.annotation.Nullable;

public class DisplaySegmentActivity extends Activity {

  private ImageView croppedImageView;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_display_segment);

    init();
    loadCroppedImage();
  }

  private void init() {
    croppedImageView = findViewById(R.id.croppedImageView);
  }

  private void loadCroppedImage() {
    Bitmap croppedBitmap = ImageDataManager.getInstance().getCroppedBitmap();
    if (croppedBitmap != null) {
      croppedImageView.setImageBitmap(croppedBitmap);
    }
  }
}