param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$NdkVersion = "28.2.13676358",
    [string[]]$Abis = @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
)
$ErrorActionPreference = "Stop"
if (-not $Sdk) { throw "Set ANDROID_HOME or pass -Sdk with the Android SDK directory" }
$root = Split-Path $PSScriptRoot -Parent
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python) { $python = (Get-Command python3 -ErrorAction SilentlyContinue).Source }
if (-not $python) { throw "Python is required to import the local Elite AGB reference" }
& $python (Join-Path $PSScriptRoot "prepare_native.py") `
    --reference (Join-Path $root "refs\eliteagb-0.5.2") `
    --output (Join-Path $root "native\vendor\eliteagb")
if ($LASTEXITCODE -ne 0) { throw "Reference import failed" }
$cmake = Join-Path $Sdk "cmake\3.22.1\bin\cmake.exe"
$ninja = Join-Path $Sdk "cmake\3.22.1\bin\ninja.exe"
$toolchain = Join-Path $Sdk "ndk\$NdkVersion\build\cmake\android.toolchain.cmake"
foreach ($path in @($cmake, $ninja, $toolchain)) {
    if (-not (Test-Path $path)) { throw "Required build tool missing: $path" }
}
foreach ($abi in $Abis) {
    if ($abi -notin @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) { throw "Unsupported ABI: $abi" }
    $build = Join-Path $root "build\native\$abi"
    & $cmake -S "$root\native" -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain" "-DANDROID_ABI=$abi" "-DANDROID_PLATFORM=android-21" `
        "-DCMAKE_BUILD_TYPE=RelWithDebInfo" "-DELITE_BUILD_TESTS=ON"
    if ($LASTEXITCODE -ne 0) { throw "CMake configure failed: $abi" }
    & $cmake --build $build --parallel 4
    if ($LASTEXITCODE -ne 0) { throw "Native build failed: $abi" }
}
Write-Output "Built libelite_core.so (subset only) and test executables. The APK has NOT been replaced."
