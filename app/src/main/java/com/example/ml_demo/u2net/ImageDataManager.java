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
    private Bitmap croppedBitmap;

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
    public void setData(Bitmap originalBitmap, Bitmap resultBitmap, Bitmap croppedBitmap) {
        this.originalBitmap = originalBitmap;
        this.resultBitmap = resultBitmap;
        this.croppedBitmap = croppedBitmap;
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
     * 获取裁剪图片
     */
    public Bitmap getCroppedBitmap() {
        return croppedBitmap;
    }

    /**
     * 检查数据是否可用
     */
    public boolean hasData() {
        return originalBitmap != null && resultBitmap != null && croppedBitmap != null;
    }
}