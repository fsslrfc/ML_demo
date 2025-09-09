package com.example.ml_demo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class Display3DActivity extends Activity implements SensorEventListener {
  private Bitmap currentOriginalBitmap;
  private Bitmap currentResultBitmap;
  private String currentResultString;
  
  // 模型输出尺寸常量
  public final int WIDTH_SIZE = 320;
  public final int HEIGHT_SIZE = 320;
  
  // 传感器相关
  private SensorManager sensorManager;
  private Sensor accelerometer;
  private MotionImageView motionImageView;
  
  // 传感器数据
  private float offsetX = 0f;
  private float offsetY = 0f;
  private static final float SENSITIVITY = 5f; // 灵敏度调节
  private static final float MAX_OFFSET = 20f; // 最大偏移量
  
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // 初始化传感器
    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    
    // 从内存中获取数据
    ImageDataManager dataManager = ImageDataManager.getInstance();
    currentOriginalBitmap = dataManager.getOriginalBitmap();
    currentResultString = dataManager.getResultString();

    if (currentResultString != null) {
      currentResultBitmap = BitmapFactory.decodeFile(currentResultString);
    }

    if (dataManager.hasData()) {
      setContentView(show3DView(currentOriginalBitmap, currentResultBitmap));
    } else {
      // 数据不可用，返回上一个Activity
      finish();
    }
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    // 注册传感器监听器
    if (accelerometer != null) {
      sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }
  }
  
  @Override
  protected void onPause() {
    super.onPause();
    // 取消注册传感器监听器
    sensorManager.unregisterListener(this);
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    // Activity销毁时可以选择清除数据，释放内存
    // 注意：如果有其他Activity也需要使用这些数据，可以不在这里清除
    // ImageDataManager.getInstance().clearData();
  }

  private View show3DView(Bitmap currentOriginalBitmap, Bitmap currentResultBitmap) {
    // 创建主布局
    LinearLayout mainLayout = new LinearLayout(this);
    mainLayout.setOrientation(LinearLayout.VERTICAL);
    mainLayout.setPadding(20, 20, 20, 20);
    
    // 创建自定义的MotionImageView来处理传感器输入
    motionImageView = new MotionImageView(this, currentOriginalBitmap, currentResultBitmap);
    
    // 添加到布局
    mainLayout.addView(motionImageView);
    
    return mainLayout;
  }

  /**
   * 创建合成图片：原图作为底层，预测结果放大120%作为上层
   */
  private Bitmap createCompositeBitmap(Bitmap originalBitmap, Bitmap resultBitmap) {
    int width = originalBitmap.getWidth();
    int height = originalBitmap.getHeight();
    
    // 创建合成结果图片
    Bitmap compositeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(compositeBitmap);
    
    // 1. 绘制底层原图
    canvas.drawBitmap(originalBitmap, 0, 0, null);
    
    // 2. 创建预测结果的掩码图片
    Bitmap maskBitmap = createMaskBitmap(resultBitmap);
    
    // 3. 将掩码放大120%
    int scaledWidth = (int) (width * 1.2f);
    int scaledHeight = (int) (height * 1.2f);
    Bitmap scaledMask = Bitmap.createScaledBitmap(maskBitmap, scaledWidth, scaledHeight, true);
    
    // 4. 计算居中位置
    int offsetX = (width - scaledWidth) / 2;
    int offsetY = (height - scaledHeight) / 2;
    
    // 5. 创建半透明的Paint用于叠加
    Paint overlayPaint = new Paint();
    overlayPaint.setAlpha(255); // 设置透明度 (0-255)
    
    // 6. 将放大的掩码绘制到合成图片上
    canvas.drawBitmap(scaledMask, offsetX, offsetY, overlayPaint);
    
    return compositeBitmap;
  }

  /**
   * 创建掩码图片 - 保持原始质量
   */
  private Bitmap createMaskBitmap(Bitmap resultBitmap) {
    // 直接返回原始结果图片，不进行额外缩放
    return resultBitmap;
  }
  
  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      // 获取加速度传感器数据
      float x = event.values[0];
      float y = event.values[1];
      
      // 计算偏移量（反向，因为设备向右倾斜时，图片应该向左移动）
      offsetX = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, -x * SENSITIVITY));
      offsetY = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, y * SENSITIVITY));
      
      // 更新自定义ImageView
      if (motionImageView != null) {
        motionImageView.updateOffset(offsetX, offsetY);
      }
    }
  }
  
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // 传感器精度改变时的回调，通常不需要处理
  }
  
  /**
   * 自定义ImageView，支持动态偏移绘制
   */
  private class MotionImageView extends View {
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;
    private Bitmap scaledResultBitmap;
    private Paint overlayPaint;
    private float currentOffsetX = 0f;
    private float currentOffsetY = 0f;
    
    public MotionImageView(Context context, Bitmap originalBitmap, Bitmap resultBitmap) {
      super(context);
      this.originalBitmap = originalBitmap;
      this.resultBitmap = resultBitmap;
      
      // 初始化Paint
      overlayPaint = new Paint();
      overlayPaint.setAlpha(255);
      
      // 保存原始结果图片，避免预处理时的质量损失
      if (originalBitmap != null && resultBitmap != null) {
        // 直接使用原始结果图片，在绘制时再进行高质量缩放
        scaledResultBitmap = resultBitmap;
      }
    }
    
    public void updateOffset(float offsetX, float offsetY) {
      this.currentOffsetX = offsetX;
      this.currentOffsetY = offsetY;
      invalidate(); // 触发重绘
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      
      if (originalBitmap == null || scaledResultBitmap == null) {
        return;
      }
      
      // 计算绘制位置和大小
      int viewWidth = getWidth();
      int viewHeight = getHeight();
      
      if (viewWidth == 0 || viewHeight == 0) {
        return;
      }
      
      // 计算原图的绘制位置（居中）
      float originalScale = Math.min((float) viewWidth / originalBitmap.getWidth(), 
                                   (float) viewHeight / originalBitmap.getHeight());
      int scaledOriginalWidth = (int) (originalBitmap.getWidth() * originalScale);
      int scaledOriginalHeight = (int) (originalBitmap.getHeight() * originalScale);
      int originalX = (viewWidth - scaledOriginalWidth) / 2;
      int originalY = (viewHeight - scaledOriginalHeight) / 2;
      
      // 使用Paint进行绘制
      Paint highQualityPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      
      // 绘制底层原图 - 直接在Canvas上缩放，避免创建新Bitmap
      android.graphics.Rect originalSrcRect = new android.graphics.Rect(0, 0, originalBitmap.getWidth(), originalBitmap.getHeight());
      android.graphics.Rect originalDstRect = new android.graphics.Rect(originalX, originalY, originalX + scaledOriginalWidth, originalY + scaledOriginalHeight);
      canvas.drawBitmap(originalBitmap, originalSrcRect, originalDstRect, highQualityPaint);
      
      // 计算结果图片的绘制位置（居中 + 传感器偏移）
      int resultWidth = (int) (scaledOriginalWidth * 1.2f);
      int resultHeight = (int) (scaledOriginalHeight * 1.2f);
      int baseResultX = (viewWidth - resultWidth) / 2;
      int baseResultY = (viewHeight - resultHeight) / 2;
      
      // 应用传感器偏移
      int resultX = baseResultX + (int) currentOffsetX;
      int resultY = baseResultY + (int) currentOffsetY;
      
      // 绘制上层结果图片
      android.graphics.Rect resultSrcRect = new android.graphics.Rect(0, 0, scaledResultBitmap.getWidth(), scaledResultBitmap.getHeight());
      android.graphics.Rect resultDstRect = new android.graphics.Rect(resultX, resultY, resultX + resultWidth, resultY + resultHeight);
      
      // 使用高质量Paint和透明度
      Paint resultPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      resultPaint.setAlpha(255);
      canvas.drawBitmap(scaledResultBitmap, resultSrcRect, resultDstRect, resultPaint);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      
      if (originalBitmap != null) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        
        // 保持宽高比
        float aspectRatio = (float) originalBitmap.getWidth() / originalBitmap.getHeight();
        
        if (width / aspectRatio <= height) {
          height = (int) (width / aspectRatio);
        } else {
          width = (int) (height * aspectRatio);
        }
        
        setMeasuredDimension(width, height);
      }
    }
  }
}
