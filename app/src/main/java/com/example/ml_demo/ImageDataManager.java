package com.example.ml_demo;

import android.graphics.Bitmap;

/**
 * 图片数据管理器 - 用于在Activity间传递大数据
 * 使用单例模式在内存中临时存储Bitmap和预测结果
 */
public class ImageDataManager {
    private static ImageDataManager instance;
    private Bitmap originalBitmap;
    private String resultString;

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
    public void setData(Bitmap originalBitmap, String resultPath) {
        this.originalBitmap = originalBitmap;
        this.resultString = resultPath;
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
    public String getResultString() {
        return resultString;
    }

    /**
     * 检查数据是否可用
     */
    public boolean hasData() {
        return originalBitmap != null && resultString != null;
    }
}