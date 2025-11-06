package com.example.ml_demo.u2net;

import android.graphics.Bitmap;

/**
 * 图片数据管理器 - 用于在Activity间传递大数据
 * 使用单例模式在内存中临时存储数据
 */
public class ImageDataManager {
    private static ImageDataManager instance;
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;
    private Bitmap maskBitmap;
    private Bitmap croppedBitmap;
    private Bitmap outlineBitmap;
    private Bitmap shadowBitmap;

    private ImageDataManager() {
    }

    /**
     * 获取单例实例
     */
    public static ImageDataManager getInstance() {
        if (instance == null) {
            synchronized (ImageDataManager.class) {
                if (instance == null) {
                    instance = new ImageDataManager();
                }
            }
        }
        return instance;
    }

    /**
     * 设置图片和预测数据
     */
    public void setData(Bitmap originalBitmap, Bitmap resultBitmap, Bitmap maskBitmap, Bitmap croppedBitmap, Bitmap outlineBitmap, Bitmap shadowBitmap) {
        this.originalBitmap = originalBitmap;
        this.resultBitmap = resultBitmap;
        this.maskBitmap = maskBitmap;
        this.croppedBitmap = croppedBitmap;
        this.outlineBitmap = outlineBitmap;
        this.shadowBitmap = shadowBitmap;
    }

    /**
     * 获取原始图片
     */
    public Bitmap getOriginalBitmap() {
        return originalBitmap;
    }

    /**
     * 获取预测结果
     */
    public Bitmap getResultBitmap() {
        return resultBitmap;
    }

    /**
     * 获取掩码图像
     */
    public Bitmap getMaskBitmap() {
        return maskBitmap;
    }

    /**
     * 获取裁剪图片
     */
    public Bitmap getCroppedBitmap() {
        return croppedBitmap;
    }

    /**
     * 获取描边图像
     */
    public Bitmap getOutlineBitmap() {
        return outlineBitmap;
    }

    /**
     * 获取阴影图像
     */
    public Bitmap getShadowBitmap() {
        return shadowBitmap;
    }

    /**
     * 检查数据是否可用
     */
    public boolean hasData() {
        return originalBitmap != null && resultBitmap != null && croppedBitmap != null;
    }
}