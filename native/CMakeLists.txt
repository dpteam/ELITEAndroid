cmake_minimum_required(VERSION 3.22)
project(EliteCore VERSION 0.1 LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
option(ELITE_BUILD_TESTS "Build executable subset tests (host by default)" ON)
option(ELITE_BUILD_PREVIEW "Build separate Android mesh viewer JNI, NOT the game" OFF)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/vendor/eliteagb/shipdata.c")
    message(FATAL_ERROR "Run python scripts/prepare_native.py from the repository root first. Local refs/eliteagb-0.5.2 is required.")
endif()

set(ELITE_CORE_SOURCES
    src/elite_core.c
    src/elite_mesh.c
    src/elite_flight.c
    src/elite_scene.c
    vendor/eliteagb/model_registry.c
    vendor/eliteagb/geometry_checksum.c
    vendor/eliteagb/motion_math.c
    vendor/eliteagb/motion_object.c
    vendor/eliteagb/control_helpers.c
    vendor/eliteagb/player_profiles.c
    vendor/eliteagb/autopilot_math.c
    vendor/eliteagb/shipdata.c)
add_library(elite_core_objects OBJECT ${ELITE_CORE_SOURCES})
target_include_directories(elite_core_objects PUBLIC include vendor/eliteagb)
target_compile_definitions(elite_core_objects PRIVATE HAVE_GBA_SHIPSETS HAVE_C64_KILLS USE_VANISHING_POINTS)
target_compile_options(elite_core_objects PRIVATE -Wall -Wextra -fwrapv -fsigned-char)
add_library(elite_core SHARED $<TARGET_OBJECTS:elite_core_objects>)
target_include_directories(elite_core PUBLIC include vendor/eliteagb)
if(ANDROID)
    target_link_options(elite_core PRIVATE -Wl,-z,max-page-size=16384 -Wl,--no-undefined)
endif()
if(WIN32)
    set_target_properties(elite_core PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
    if(MINGW)
        target_link_options(elite_core PRIVATE -Wl,--export-all-symbols)
    endif()
endif()

if(ELITE_BUILD_TESTS)
    enable_testing()
    add_executable(elite_core_tests tests/core_tests.c $<TARGET_OBJECTS:elite_core_objects>)
    target_include_directories(elite_core_tests PRIVATE include vendor/eliteagb)
    target_compile_definitions(elite_core_tests PRIVATE HAVE_GBA_SHIPSETS HAVE_C64_KILLS USE_VANISHING_POINTS)
    add_test(NAME elite_core_tests COMMAND elite_core_tests)
endif()

if(ELITE_BUILD_PREVIEW)
    if(NOT ANDROID)
        message(FATAL_ERROR "The JNI preview target requires the Android toolchain")
    endif()
    add_library(elite_preview SHARED src/preview_jni.c)
    target_link_libraries(elite_preview PRIVATE elite_core)
    target_link_options(elite_preview PRIVATE -Wl,-z,max-page-size=16384 -Wl,--no-undefined)
endif()
