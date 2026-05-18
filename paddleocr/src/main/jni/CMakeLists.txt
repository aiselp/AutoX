project(ppocrv5ncnn)

cmake_minimum_required(VERSION 3.10)

# 16KB page size alignment support
add_link_options("-Wl,-z,max-page-size=16384")

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-2.4.13.7-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20260113-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(ppocrv5ncnn SHARED ppocrv5ncnn.cpp ppocrv5.cpp)

target_link_libraries(ppocrv5ncnn ncnn ${OpenCV_LIBS})
