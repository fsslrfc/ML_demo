package com.example.ml_demo.patchmatch;

/**
 * PatchMatch算法的JNI接口类
 * 用于调用C++实现的图像修复功能
 */
public class PatchMatchNative {

    // 加载native库
    static {
        System.loadLibrary("patchmatch");
    }

    /**
     * 执行图像修复
     * @param imagePath 输入图像路径
     * @param maskPath mask图像路径（白色区域为需要修复的区域）
     * @param outputPath 输出图像路径
     * @return 处理结果信息（包含PSNR和SSIM指标）
     */
    public native String inpaintImage(String imagePath, String maskPath, String outputPath);
}
