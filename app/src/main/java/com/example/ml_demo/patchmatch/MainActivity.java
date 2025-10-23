package com.example.ml_demo.patchmatch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ml_demo.EmptyActivity;
import com.example.ml_demo.R;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends EmptyActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_MASK_PICK = 2;
    private static final int REQUEST_PERMISSION = 100;

    private ImageView imageViewOriginal;
    private ImageView imageViewMask;
    private ImageView imageViewResult;
    private Button btnSelectImage;
    private Button btnSelectMask;
    private Button btnProcess;
    private ProgressBar progressBar;
    private TextView textViewResult;

    private String imagePath;
    private String maskPath;
    private String outputPath;

    private PatchMatchNative patchMatchNative;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patchmatch);
        if (OpenCVLoader.initLocal()) {
            (Toast.makeText(this, "OpenCV 初始化成功", Toast.LENGTH_LONG)).show();
        } else {
            (Toast.makeText(this, "OpenCV 初始化失败！", Toast.LENGTH_LONG)).show();
            return;
        }


        // 初始化JNI
        patchMatchNative = new PatchMatchNative();

        // 初始化视图
        initViews();

        // 请求权限
        checkPermissions();
    }

    private void initViews() {
        imageViewOriginal = findViewById(R.id.imageViewOriginal);
        imageViewMask = findViewById(R.id.imageViewMask);
        imageViewResult = findViewById(R.id.imageViewResult);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSelectMask = findViewById(R.id.btnSelectMask);
        btnProcess = findViewById(R.id.btnProcess);
        progressBar = findViewById(R.id.progressBar);
        textViewResult = findViewById(R.id.textViewResult);

        btnSelectImage.setOnClickListener(v -> selectImage());
        btnSelectMask.setOnClickListener(v -> selectMask());
        btnProcess.setOnClickListener(v -> processImage());
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_PERMISSION);
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void selectMask() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_MASK_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            try {
                if (requestCode == REQUEST_IMAGE_PICK) {
                    // 保存原始图像
                    imagePath = saveImageToInternalStorage(selectedImageUri, "input_image.jpg");
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                    imageViewOriginal.setImageBitmap(bitmap);
                    Toast.makeText(this, "图像已选择", Toast.LENGTH_SHORT).show();
                } else if (requestCode == REQUEST_MASK_PICK) {
                    // 保存mask图像
                    maskPath = saveImageToInternalStorage(selectedImageUri, "input_mask.jpg");
                    Bitmap bitmap = BitmapFactory.decodeFile(maskPath);
                    imageViewMask.setImageBitmap(bitmap);
                    Toast.makeText(this, "Mask已选择", Toast.LENGTH_SHORT).show();
                }

                // 检查是否可以开始处理
                if (imagePath != null && maskPath != null) {
                    btnProcess.setEnabled(true);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "图像加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String saveImageToInternalStorage(Uri imageUri, String filename) throws IOException {
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

    private void processImage() {
        if (imagePath == null || maskPath == null) {
            Toast.makeText(this, "请先选择图像和Mask", Toast.LENGTH_SHORT).show();
            return;
        }

        // 设置输出路径
        outputPath = new File(getFilesDir(), "output_image.jpg").getAbsolutePath();

        // 显示进度条
        progressBar.setVisibility(View.VISIBLE);
        btnProcess.setEnabled(false);
        textViewResult.setText("处理中，请稍候...");

        // 在后台线程执行
        new Thread(() -> {
            try {
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

                Photo.inpaint(src, mask, result, 3, Photo.INPAINT_TELEA);

                Bitmap resultBitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(result, resultBitmap);

                // 在主线程更新UI
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);

                    if (!result.empty()) {
                        // 显示结果图像
                        imageViewResult.setImageBitmap(resultBitmap);
                        textViewResult.setText("Success");
                        Toast.makeText(MainActivity.this, "处理完成！", Toast.LENGTH_SHORT).show();
                    } else {
                        textViewResult.setText("Failed");
                        Toast.makeText(MainActivity.this, "处理失败！", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    textViewResult.setText("错误: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "处理异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });


//                // 调用JNI方法
//                String result = patchMatchNative.inpaintImage(imagePath, maskPath, outputPath);
//
//                // 在主线程更新UI
//                runOnUiThread(() -> {
//                    progressBar.setVisibility(View.GONE);
//                    btnProcess.setEnabled(true);
//
//                    if (result.startsWith("Success")) {
//                        // 显示结果图像
//                        Bitmap resultBitmap = BitmapFactory.decodeFile(outputPath);
//                        imageViewResult.setImageBitmap(resultBitmap);
//                        textViewResult.setText(result);
//                        Toast.makeText(MainActivity.this, "处理完成！", Toast.LENGTH_SHORT).show();
//                    } else {
//                        textViewResult.setText(result);
//                        Toast.makeText(MainActivity.this, "处理失败: " + result, Toast.LENGTH_LONG).show();
//                    }
//                });
//            } catch (Exception e) {
//                e.printStackTrace();
//                runOnUiThread(() -> {
//                    progressBar.setVisibility(View.GONE);
//                    btnProcess.setEnabled(true);
//                    textViewResult.setText("错误: " + e.getMessage());
//                    Toast.makeText(MainActivity.this, "处理异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
//                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要存储权限才能使用此功能", Toast.LENGTH_LONG).show();
            }
        }
    }
}
