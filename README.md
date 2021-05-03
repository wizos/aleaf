# aleaf

Shows how to build a VPN app for Android using [leaf](https://github.com/eycorsican/leaf).

## Dependencies

* Rust
* GCC/clang
* Make
* SDK
* NDK
* LLVM (Windows host only, see below)

## Building

### Linux

```
export ANDROID_HOME=/path/to/sdk
export NDK_HOME=/path/to/sdk/ndk-bundle

rustup target add aarch64-linux-android x86_64-linux-android

git clone https://github.com/eycorsican/aleaf
cd aleaf

./app/src/main/rust/leaf-android/build.sh debug
```

Refer [here](https://mozilla.github.io/firefox-browser-architecture/experiments/2017-09-21-rust-on-android.html) and [here](https://github.com/eycorsican/aleaf/blob/master/.github/workflows/ci.yml) for more details.

### Windows

Building on Windows is a little tricky. This is because leaf relies on [bindgen](https://github.com/rust-lang/rust-bindgen) to generate bindings, which requires `libclang.so` or `libclang.dll` to exist. The Linux build of NDK has `libclang.so.11git` (and many other libs) included under `toolchains/llvm/prebuilt/linux-x86_64/lib64`, but when it comes to the Windows build, they are all missing. You could refer to [this issue](https://github.com/android/ndk/issues/1491) for detail.

To solve this problem, we could use the Windows build of LLVM. Note that it is for bindgen only, not to confuse it with the LLVM toolchain in NDK.

After installing LLVM, set `LLVM_WIN64_HOME` environment variable to the path of your LLVM installation.

```powershell
$Env:LLVM_WIN64_HOME = 'C:\path\to\llvm-win64'
$Env:ANDROID_HOME = 'C:\path\to\android-sdk'
$Env:NDK_HOME = 'C:\path\to\android-ndk'

rustup target add aarch64-linux-android x86_64-linux-android

git clone https://github.com/eycorsican/aleaf
cd aleaf

& .\app\src\main\rust\leaf-android\build.ps1 debug
# For release build, run:
# & .\app\src\main\rust\leaf-android\build.ps1 release
```

> Under the hood, the `build.ps1` sets `LIBCLANG_PATH` and `BINDGEN_EXTRA_CLANG_ARGS` environment variables that are used by bindgen. Setting `LIBCLANG_PATH` allows bindgen to consume a valid `libclang.dll`, and `BINDGEN_EXTRA_CLANG_ARGS` specifies extra args that are specified internally by `libclang.so` on Android NDK Linux build, but missing on LLVM Windows build.
> You could run `$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang -c some-file.c -v` on Linux to get the args that are specified internally.
> The LLVM toolchain in NDK is v11.0.5, but v11.1.0 for Windows should work.
