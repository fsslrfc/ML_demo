cmake_minimum_required(VERSION 3.22.1)

project("patchmatch")

# 设置C++标准
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 使用共享STL（OpenCV需要）
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# 手动设置OpenCV路径（从AAR中提取）
set(OPENCV_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/../../../build/opencv/include")
set(OPENCV_LIB_DIR "${CMAKE_SOURCE_DIR}/../../../build/opencv/libs/${ANDROID_ABI}")

# 调试信息
message(STATUS "CMAKE_SOURCE_DIR: ${CMAKE_SOURCE_DIR}")
message(STATUS "ANDROID_ABI: ${ANDROID_ABI}")
message(STATUS "OPENCV_INCLUDE_DIR: ${OPENCV_INCLUDE_DIR}")
message(STATUS "OPENCV_LIB_DIR: ${OPENCV_LIB_DIR}")

# 包含头文件目录
include_directories(
        ${CMAKE_SOURCE_DIR}/include
        ${OPENCV_INCLUDE_DIR}
)

# 添加源文件
add_library(
        patchmatch
        SHARED
        patchmatch_jni.cpp
        inpaint.cpp
        maskedimage.cpp
        nearestneighborfield.cpp
        qualitymesures.cpp
)

# 直接指定OpenCV库文件路径
set(OPENCV_LIB "${OPENCV_LIB_DIR}/libopencv_java4.so")
message(STATUS "OPENCV_LIB: ${OPENCV_LIB}")

# 检查文件是否存在
if(EXISTS "${OPENCV_LIB}")
    message(STATUS "Found OpenCV library: ${OPENCV_LIB}")
else()
    message(FATAL_ERROR "OpenCV library not found at: ${OPENCV_LIB}")
endif()

# 链接库
target_link_libraries(
        patchmatch
        ${OPENCV_LIB}
        log
        android
)
