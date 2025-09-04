package com.example.ml_demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class Display3DActivity extends Activity {
    
    private ImageView croppedImageView;
    private TextView titleText;
    private Button backButton;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_3d);
        
        initializeViews();
        loadCroppedImage();
    }
    
    private void initializeViews() {
        croppedImageView = findViewById(R.id.croppedImageView);
        titleText = findViewById(R.id.titleText);
        backButton = findViewById(R.id.backButton);
        
        titleText.setText("显著性裁剪结果");
        
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // 返回上一个Activity
            }
        });
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