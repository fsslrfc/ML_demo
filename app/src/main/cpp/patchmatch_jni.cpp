#include <jni.h>
#include <string>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <opencv2/core/core_c.h>
#include <opencv2/imgproc/imgproc_c.h>
#include "include/defineall.h"

#define LOG_TAG "PatchMatchJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局变量定义（来自原始代码）
double* G_globalSimilarity = NULL;
int G_initSim = 0;

// 辅助函数（来自原始代码）
double max1(double a, double b) {
    return (a + b + fabs(a - b)) / 2;
}

double min1(double a, double b) {
    return (a + b - fabs(a - b)) / 2;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_ml_1demo_patchmatch_PatchMatchNative_inpaintImage(
        JNIEnv* env,
        jobject /* this */,
        jstring imagePath,
        jstring maskPath,
        jstring outputPath) {

    const char* image_path = env->GetStringUTFChars(imagePath, nullptr);
    const char* mask_path = env->GetStringUTFChars(maskPath, nullptr);
    const char* output_path = env->GetStringUTFChars(outputPath, nullptr);

    LOGD("Starting PatchMatch inpainting...");
    LOGD("Image: %s", image_path);
    LOGD("Mask: %s", mask_path);
    LOGD("Output: %s", output_path);

    try {
        // 设置随机种子
        srand((unsigned)time(0));

        // 读取图像
        cv::Mat ori_image = cv::imread(image_path, cv::IMREAD_COLOR);
        cv::Mat mask_image = cv::imread(mask_path, cv::IMREAD_GRAYSCALE);

        if (ori_image.empty()) {
            LOGE("Failed to load image: %s", image_path);
            env->ReleaseStringUTFChars(imagePath, image_path);
            env->ReleaseStringUTFChars(maskPath, mask_path);
            env->ReleaseStringUTFChars(outputPath, output_path);
            return env->NewStringUTF("Error: Failed to load image");
        }

        if (mask_image.empty()) {
            LOGE("Failed to load mask: %s", mask_path);
            env->ReleaseStringUTFChars(imagePath, image_path);
            env->ReleaseStringUTFChars(maskPath, mask_path);
            env->ReleaseStringUTFChars(outputPath, output_path);
            return env->NewStringUTF("Error: Failed to load mask");
        }

        // 转换为IplImage
        IplImage ori_ipl_img_data = cvIplImage(ori_image);
        IplImage* ori_ipl_img = &ori_ipl_img_data;

        int height = ori_image.rows;
        int width = ori_image.cols;

        LOGD("Image size: %dx%d", width, height);

        // 生成mask数组
        int** mask = (int**)calloc(height, sizeof(int*));
        for (int i = 0; i < height; i++) {
            mask[i] = (int*)calloc(width, sizeof(int));
        }

        uchar* data = (uchar*)mask_image.data;
        int step = mask_image.cols;
        int channels = mask_image.channels();

        for (int i = 0; i < height; ++i) {
            for (int j = 0; j < width; ++j) {
                if (data[i * step + j * channels] == 255) {
                    mask[i][j] = 1;
                }
            }
        }

        LOGD("Starting inpainting process...");

        // 执行inpainting
        Inpaint_P inp = initInpaint();
        IplImage* output_ipl_img = inpaint(inp, ori_ipl_img, mask, 2);

        if (output_ipl_img == NULL) {
            LOGE("Inpainting failed");
            // 释放内存
            for (int i = 0; i < height; ++i) {
                free(mask[i]);
            }
            free(mask);
            freeInpaintingPyramid(inp);

            env->ReleaseStringUTFChars(imagePath, image_path);
            env->ReleaseStringUTFChars(maskPath, mask_path);
            env->ReleaseStringUTFChars(outputPath, output_path);
            return env->NewStringUTF("Error: Inpainting process failed");
        }

        // 转换回cv::Mat并保存
        cv::Mat output_image = cv::cvarrToMat(output_ipl_img, false);
        bool save_success = cv::imwrite(output_path, output_image);

        LOGD("Inpainting completed. Save result: %s", save_success ? "success" : "failed");

        // 计算质量指标
        double psnr = PSNR(ori_ipl_img, output_ipl_img);
        double ssim = SSIM(ori_ipl_img, output_ipl_img);

        LOGD("PSNR: %f dB, SSIM: %f", psnr, ssim);

        // 释放内存
        cvReleaseImage(&output_ipl_img);
        for (int i = 0; i < height; ++i) {
            free(mask[i]);
        }
        free(mask);
        freeInpaintingPyramid(inp);

        env->ReleaseStringUTFChars(imagePath, image_path);
        env->ReleaseStringUTFChars(maskPath, mask_path);
        env->ReleaseStringUTFChars(outputPath, output_path);

        if (save_success) {
            char result[256];
            snprintf(result, sizeof(result), "Success! PSNR: %.2f dB, SSIM: %.4f", psnr, ssim);
            return env->NewStringUTF(result);
        } else {
            return env->NewStringUTF("Error: Failed to save output image");
        }

    } catch (const std::exception& e) {
        LOGE("Exception: %s", e.what());
        env->ReleaseStringUTFChars(imagePath, image_path);
        env->ReleaseStringUTFChars(maskPath, mask_path);
        env->ReleaseStringUTFChars(outputPath, output_path);
        return env->NewStringUTF(("Error: " + std::string(e.what())).c_str());
    }
}
