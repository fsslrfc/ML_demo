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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class Display3dActivity extends Activity implements SensorEventListener {
  private Bitmap currentOriginalBitmap;
  private Bitmap currentResultBitmap;
  private String currentResultString;
  
  // 模型输出尺寸常量
  public final int WIDTH_SIZE = 320;
  public final int HEIGHT_SIZE = 320;
  
  // 传感器相关
  private SensorManager sensorManager;
  private Sensor accelerometer;
  private Sensor gyroscope;
  private MotionImageView motionImageView;
  
  // 传感器数据
  private float offsetX = 0f;
  private float offsetY = 0f;
  private float velocityX = 0f;
  private float velocityY = 0f;
  
  // 传感器数据滤波
  private float[] accelHistory = new float[6]; // 存储最近3次的X,Y加速度数据
  private int accelIndex = 0;
  
  // 传感器融合参数 - 改为可调节变量
  private float accelSensitivity = 50f; // 加速度灵敏度 (默认值)
  private float gyroSensitivity = 50f; // 陀螺仪灵敏度 (默认值)
  private float maxOffset = 50f; // 最大偏移量 (默认值)
  private float dampingFactor = 0.8f; // 阻尼系数 (默认值)
  private float velocityThreshold = 0.05f; // 速度阈值 (默认值)
  private float springStrength = 0.05f; // 弹簧回弹强度 (默认值)
  
  // 默认值常量（用于重置）
  private static final float DEFAULT_ACCEL_SENSITIVITY = 50f;
  private static final float DEFAULT_GYRO_SENSITIVITY = 50f;
  private static final float DEFAULT_MAX_OFFSET = 50f;
  private static final float DEFAULT_DAMPING_FACTOR = 0.8f;
  private static final float DEFAULT_VELOCITY_THRESHOLD = 0.05f;
  private static final float DEFAULT_SPRING_STRENGTH = 0.05f;
  
  // UI控件
  private TextView accelSensitivityText;
  private TextView gyroSensitivityText;
  private TextView maxOffsetText;
  private TextView dampingFactorText;
  private TextView velocityThresholdText;
  private TextView springStrengthText;
  
  // SeekBar控件（用于重置）
  private SeekBar accelSeekBar;
  private SeekBar gyroSeekBar;
  private SeekBar maxOffsetSeekBar;
  private SeekBar dampingSeekBar;
  private SeekBar velocitySeekBar;
  private SeekBar springSeekBar;
  
  // 时间相关
  private long lastUpdateTime = 0;
  
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 初始化传感器
    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    
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
    if (gyroscope != null) {
      sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
    }
    lastUpdateTime = System.currentTimeMillis();
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
    // 创建ScrollView作为根布局
    ScrollView scrollView = new ScrollView(this);
    
    // 创建主布局
    LinearLayout mainLayout = new LinearLayout(this);
    mainLayout.setOrientation(LinearLayout.VERTICAL);
    mainLayout.setPadding(20, 20, 20, 20);
    
    // 添加参数控制面板
    LinearLayout controlPanel = createControlPanel();
    mainLayout.addView(controlPanel);
    
    // 添加分隔线
    View divider = new View(this);
    divider.setBackgroundColor(0xFF888888);
    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 2);
    dividerParams.setMargins(0, 20, 0, 20);
    divider.setLayoutParams(dividerParams);
    mainLayout.addView(divider);
    
    // 创建自定义的MotionImageView来处理传感器输入
    motionImageView = new MotionImageView(this, currentOriginalBitmap, currentResultBitmap);
    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    motionImageView.setLayoutParams(imageParams);
    mainLayout.addView(motionImageView);
    
    scrollView.addView(mainLayout);
    scrollView.setFillViewport(true);
    return scrollView;
  }
  
  /**
   * 创建参数控制面板
   */
  private LinearLayout createControlPanel() {
    LinearLayout controlPanel = new LinearLayout(this);
    controlPanel.setOrientation(LinearLayout.VERTICAL);
    controlPanel.setPadding(10, 10, 10, 10);
    
    // 标题
    TextView titleText = new TextView(this);
    titleText.setText("传感器参数调节");
    titleText.setTextSize(18);
    titleText.setTextColor(0xFF333333);
    titleText.setPadding(0, 0, 0, 20);
    controlPanel.addView(titleText);
    
    // 加速度灵敏度
    controlPanel.addView(createParameterControl(
        "加速度灵敏度", accelSensitivity, 0f, 100f,
        (progress) -> {
          accelSensitivity = progress;
          accelSensitivityText.setText(String.format("%.0f", accelSensitivity));
        },
        (text) -> accelSensitivityText = text,
        (seekBar) -> accelSeekBar = seekBar
    ));
    
    // 陀螺仪灵敏度
    controlPanel.addView(createParameterControl(
        "陀螺仪灵敏度", gyroSensitivity, 0f, 100f,
        (progress) -> {
          gyroSensitivity = progress;
          gyroSensitivityText.setText(String.format("%.0f", gyroSensitivity));
        },
        (text) -> gyroSensitivityText = text,
        (seekBar) -> gyroSeekBar = seekBar
    ));
    
    // 最大偏移量
    controlPanel.addView(createParameterControl(
        "最大偏移量", maxOffset, 0f, 500f,
        (progress) -> {
          maxOffset = progress * 5;
          maxOffsetText.setText(String.format("%.0f", maxOffset));
        },
        (text) -> maxOffsetText = text,
        (seekBar) -> maxOffsetSeekBar = seekBar
    ));
    
    // 阻尼系数
    controlPanel.addView(createParameterControl(
        "阻尼系数", dampingFactor, 0.00f, 1.00f,
        (progress) -> {
          dampingFactor = progress * 1.00f / 100f;
          dampingFactorText.setText(String.format("%.2f", dampingFactor));
        },
        (text) -> dampingFactorText = text,
        (seekBar) -> dampingSeekBar = seekBar
    ));
    
    // 速度阈值
    controlPanel.addView(createParameterControl(
        "速度阈值", velocityThreshold, 0.00f, 1.00f,
        (progress) -> {
          velocityThreshold = progress * 1.00f / 100f;
          velocityThresholdText.setText(String.format("%.2f", velocityThreshold));
        },
        (text) -> velocityThresholdText = text,
        (seekBar) -> velocitySeekBar = seekBar
    ));
    
    // 弹簧回弹强度
    controlPanel.addView(createParameterControl(
        "弹簧回弹强度", springStrength, 0.00f, 1.00f,
        (progress) -> {
          springStrength = progress * 1.00f / 100f;
          springStrengthText.setText(String.format("%.2f", springStrength));
        },
        (text) -> springStrengthText = text,
        (seekBar) -> springSeekBar = seekBar
    ));

    return controlPanel;
  }
  
  /**
   * 重置所有参数为默认值
   */
  private void resetToDefaults() {
    // 重置参数值
    accelSensitivity = DEFAULT_ACCEL_SENSITIVITY;
    gyroSensitivity = DEFAULT_GYRO_SENSITIVITY;
    maxOffset = DEFAULT_MAX_OFFSET;
    dampingFactor = DEFAULT_DAMPING_FACTOR;
    velocityThreshold = DEFAULT_VELOCITY_THRESHOLD;
    springStrength = DEFAULT_SPRING_STRENGTH;
    
    // 更新UI显示
    accelSensitivityText.setText(String.format("%.2f", accelSensitivity));
    gyroSensitivityText.setText(String.format("%.2f", gyroSensitivity));
    maxOffsetText.setText(String.format("%.1f", maxOffset));
    dampingFactorText.setText(String.format("%.3f", dampingFactor));
    velocityThresholdText.setText(String.format("%.3f", velocityThreshold));
    springStrengthText.setText(String.format("%.3f", springStrength));
    
    // 更新SeekBar位置
    accelSeekBar.setProgress((int) ((accelSensitivity - 0.1f) / 9.9f * 100));
    gyroSeekBar.setProgress((int) ((gyroSensitivity - 1f) / 49f * 100));
    maxOffsetSeekBar.setProgress((int) ((maxOffset - 10f) / 90f * 100));
    dampingSeekBar.setProgress((int) ((dampingFactor - 0.5f) / 0.49f * 100));
    velocitySeekBar.setProgress((int) ((velocityThreshold - 0.01f) / 0.19f * 100));
    springSeekBar.setProgress((int) ((springStrength - 0.01f) / 0.19f * 100));
    
    // 重置传感器状态
    offsetX = 0f;
    offsetY = 0f;
    velocityX = 0f;
    velocityY = 0f;
  }
  
  /**
   * 创建单个参数控制组件
   */
  private LinearLayout createParameterControl(String name, float currentValue, float minValue, float maxValue,
                                            ParameterChangeListener listener, TextViewSetter textSetter, SeekBarSetter seekBarSetter) {
    LinearLayout paramLayout = new LinearLayout(this);
    paramLayout.setOrientation(LinearLayout.VERTICAL);
    paramLayout.setPadding(0, 10, 0, 10);
    
    // 参数名称和当前值
    LinearLayout headerLayout = new LinearLayout(this);
    headerLayout.setOrientation(LinearLayout.HORIZONTAL);
    
    TextView nameText = new TextView(this);
    nameText.setText(name + ": ");
    nameText.setTextSize(14);
    nameText.setTextColor(0xFF666666);
    headerLayout.addView(nameText);
    
    TextView valueText = new TextView(this);
    valueText.setText(String.format("%.3f", currentValue));
    valueText.setTextSize(14);
    valueText.setTextColor(0xFF333333);
    textSetter.setText(valueText);
    headerLayout.addView(valueText);
    
    paramLayout.addView(headerLayout);
    
    // SeekBar
    SeekBar seekBar = new SeekBar(this);
    seekBar.setMax(100);
    seekBar.setProgress((int) ((currentValue - minValue) / (maxValue - minValue) * 100));
    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          listener.onParameterChanged(progress);
        }
      }
      
      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}
      
      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });
    
    seekBarSetter.setSeekBar(seekBar);
    paramLayout.addView(seekBar);
    
    return paramLayout;
  }
  
  // 接口定义
  private interface ParameterChangeListener {
    void onParameterChanged(int progress);
  }
  
  private interface TextViewSetter {
    void setText(TextView textView);
  }
  
  private interface SeekBarSetter {
    void setSeekBar(SeekBar seekBar);
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
    long currentTime = System.currentTimeMillis();
    float deltaTime = (currentTime - lastUpdateTime) / 1000.0f; // 转换为秒
    lastUpdateTime = currentTime;
    
    // 限制deltaTime避免异常值
    if (deltaTime > 0.1f) deltaTime = 0.1f;
    
    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      // 获取加速度传感器数据并进行滤波
      float rawAccelX = event.values[0];
      float rawAccelY = event.values[1];
      
      // 存储历史数据用于滤波
      accelHistory[accelIndex * 2] = rawAccelX;
      accelHistory[accelIndex * 2 + 1] = rawAccelY;
      accelIndex = (accelIndex + 1) % 3;
      
      // 计算滤波后的加速度（简单移动平均）
      float filteredAccelX = 0, filteredAccelY = 0;
      for (int i = 0; i < 3; i++) {
        filteredAccelX += accelHistory[i * 2];
        filteredAccelY += accelHistory[i * 2 + 1];
      }
      filteredAccelX /= 3;
      filteredAccelY /= 3;
      
      // 使用滤波后的加速度数据更新位置（重力感应效果）
      float targetOffsetX = Math.max(-maxOffset, Math.min(maxOffset, -filteredAccelX * accelSensitivity));
      float targetOffsetY = Math.max(-maxOffset, Math.min(maxOffset, filteredAccelY * accelSensitivity));
      
      // 添加弹簧效果，让图片有回弹感
      float springForceX = -offsetX * springStrength;
      float springForceY = -offsetY * springStrength;
      
      // 平滑过渡到目标位置，结合弹簧力
      offsetX += (targetOffsetX - offsetX) * 0.12f + springForceX;
      offsetY += (targetOffsetY - offsetY) * 0.12f + springForceY;
      
    } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
      // 获取陀螺仪数据（角速度）
      float gyroX = event.values[0]; // 绕X轴旋转（俯仰）
      float gyroY = event.values[1]; // 绕Y轴旋转（偏航）
      
      // 使用陀螺仪数据更新速度
      velocityX += -gyroY * gyroSensitivity * deltaTime; // 注意坐标系转换
      velocityY += gyroX * gyroSensitivity * deltaTime;
      
      // 应用阻尼，让运动逐渐停止
      velocityX *= dampingFactor;
      velocityY *= dampingFactor;
      
      // 如果速度太小，直接设为0
      if (Math.abs(velocityX) < velocityThreshold) velocityX = 0;
      if (Math.abs(velocityY) < velocityThreshold) velocityY = 0;
      
      // 根据速度更新位置
      offsetX += velocityX * deltaTime;
      offsetY += velocityY * deltaTime;
      
      // 限制偏移范围
      offsetX = Math.max(-maxOffset, Math.min(maxOffset, offsetX));
      offsetY = Math.max(-maxOffset, Math.min(maxOffset, offsetY));
    }
    
    // 更新自定义ImageView
    if (motionImageView != null) {
      motionImageView.updateOffset(offsetX, offsetY);
    }
  }
  
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // 传感器精度改变时的回调，通常不需要处理
  }
  
  /**
   * 自定义ImageView，支持动态偏移绘制和平滑动画
   */
  private class MotionImageView extends View {
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;
    private Bitmap scaledResultBitmap;
    private Paint overlayPaint;
    private float currentOffsetX = 0f;
    private float currentOffsetY = 0f;
    private float targetOffsetX = 0f;
    private float targetOffsetY = 0f;
    
    // 动画相关
    private static final float ANIMATION_SMOOTHNESS = 0.15f; // 动画平滑度
    private boolean isAnimating = false;
    
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
      this.targetOffsetX = offsetX;
      this.targetOffsetY = offsetY;
      
      // 启动平滑动画
      if (!isAnimating) {
        isAnimating = true;
        startSmoothAnimation();
      }
    }
    
    /**
     * 启动平滑动画
     */
    private void startSmoothAnimation() {
      post(new Runnable() {
        @Override
        public void run() {
          // 计算当前位置到目标位置的差距
          float deltaX = targetOffsetX - currentOffsetX;
          float deltaY = targetOffsetY - currentOffsetY;
          
          // 如果差距很小，直接设置为目标值并停止动画
          if (Math.abs(deltaX) < 0.1f && Math.abs(deltaY) < 0.1f) {
            currentOffsetX = targetOffsetX;
            currentOffsetY = targetOffsetY;
            isAnimating = false;
            invalidate();
            return;
          }
          
          // 平滑插值
          currentOffsetX += deltaX * ANIMATION_SMOOTHNESS;
          currentOffsetY += deltaY * ANIMATION_SMOOTHNESS;
          
          invalidate(); // 触发重绘
          
          // 继续动画
          if (isAnimating) {
            postDelayed(this, 16); // 约60FPS
          }
        }
      });
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
      
      // 计算原图的绘制位置（宽度match_parent，高度按比例缩放）
      float originalScale = (float) viewWidth / originalBitmap.getWidth();
      int scaledOriginalWidth = viewWidth; // 宽度填满
      int scaledOriginalHeight = (int) (originalBitmap.getHeight() * originalScale);
      int originalX = 0; // 左对齐
      int originalY = (viewHeight - scaledOriginalHeight) / 2; // 垂直居中
      
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
        
        // 宽度match_parent，高度根据图片比例计算
        float aspectRatio = (float) originalBitmap.getHeight() / originalBitmap.getWidth();
        int height = (int) (width * aspectRatio);
        
        // 确保高度至少有一个最小值，避免过小
        height = Math.max(height, 400);
        
        setMeasuredDimension(width, height);
      }
    }
  }
}
