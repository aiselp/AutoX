#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "kleidicv" for configuration "Release"
set_property(TARGET kleidicv APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(kleidicv PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/3rdparty/libs/arm64-v8a/libkleidicv.a"
  )

list(APPEND _cmake_import_check_targets kleidicv )
list(APPEND _cmake_import_check_files_for_kleidicv "${_IMPORT_PREFIX}/sdk/native/3rdparty/libs/arm64-v8a/libkleidicv.a" )

# Import target "kleidicv_thread" for configuration "Release"
set_property(TARGET kleidicv_thread APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(kleidicv_thread PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/3rdparty/libs/arm64-v8a/libkleidicv_thread.a"
  )

list(APPEND _cmake_import_check_targets kleidicv_thread )
list(APPEND _cmake_import_check_files_for_kleidicv_thread "${_IMPORT_PREFIX}/sdk/native/3rdparty/libs/arm64-v8a/libkleidicv_thread.a" )

# Import target "kleidicv_hal" for configuration "Release"
set_property(TARGET kleidicv_hal APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(kleidicv_hal PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/3rdparty/libs/arm64-v8a/libkleidicv_hal.a"
  )

list(APPEND _cmake_import_check_targets kleidicv_hal )
list(APPEND _cmake_import_check_files_for_kleidicv_hal "${_IMPORT_PREFIX}/sdk/native/3rdparty/libs/arm64-v8a/libkleidicv_hal.a" )

# Import target "opencv_core" for configuration "Release"
set_property(TARGET opencv_core APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(opencv_core PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_core.a"
  )

list(APPEND _cmake_import_check_targets opencv_core )
list(APPEND _cmake_import_check_files_for_opencv_core "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_core.a" )

# Import target "opencv_imgproc" for configuration "Release"
set_property(TARGET opencv_imgproc APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(opencv_imgproc PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_imgproc.a"
  )

list(APPEND _cmake_import_check_targets opencv_imgproc )
list(APPEND _cmake_import_check_files_for_opencv_imgproc "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_imgproc.a" )

# Import target "opencv_photo" for configuration "Release"
set_property(TARGET opencv_photo APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(opencv_photo PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_photo.a"
  )

list(APPEND _cmake_import_check_targets opencv_photo )
list(APPEND _cmake_import_check_files_for_opencv_photo "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_photo.a" )

# Import target "opencv_video" for configuration "Release"
set_property(TARGET opencv_video APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(opencv_video PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_video.a"
  )

list(APPEND _cmake_import_check_targets opencv_video )
list(APPEND _cmake_import_check_files_for_opencv_video "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_video.a" )

# Import target "opencv_features2d" for configuration "Release"
set_property(TARGET opencv_features2d APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(opencv_features2d PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_features2d.a"
  )

list(APPEND _cmake_import_check_targets opencv_features2d )
list(APPEND _cmake_import_check_files_for_opencv_features2d "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_features2d.a" )

# Import target "opencv_highgui" for configuration "Release"
set_property(TARGET opencv_highgui APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(opencv_highgui PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "CXX"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_highgui.a"
  )

list(APPEND _cmake_import_check_targets opencv_highgui )
list(APPEND _cmake_import_check_files_for_opencv_highgui "${_IMPORT_PREFIX}/sdk/native/staticlibs/arm64-v8a/libopencv_highgui.a" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
