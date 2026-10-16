cmake_minimum_required(VERSION 3.22)
project(EliteCore VERSION 0.1 LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
option(ELITE_BUILD_TESTS "Build executable subset tests (host by default)" ON)

set(ELITE_CORE_SOURCES
    src/elite_core.c
    vendor/eliteagb/geometry_checksum.c
    vendor/eliteagb/motion_math.c
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
