#!/usr/bin/env bash
# Builds a minimal static OpenCV (core + imgproc) for the host machine so the quality suite
# runs the exact engine sources on macOS and on the Ubuntu CI runner (research R1, R4).
# Idempotent: exits immediately when the install prefix already exists.
set -euo pipefail

VERSION="${OPENCV_VERSION:-4.13.0}"
SHA256="${OPENCV_SOURCE_SHA256:-$(grep '^opencvSourceSha256' "$(dirname "$0")/../gradle/libs.versions.toml" | sed 's/.*= *"\(.*\)"/\1/')}"
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
[ "$ARCH" = "aarch64" ] && ARCH=arm64
CACHE="${PLAINPAPER_CACHE:-$HOME/.cache/plainpaper}"
PREFIX="$CACHE/opencv-$VERSION-$OS-$ARCH"

if [ -f "$PREFIX/lib/cmake/opencv4/OpenCVConfig.cmake" ]; then
  echo "OpenCV $VERSION already installed at $PREFIX"
  exit 0
fi

command -v cmake >/dev/null || { echo "cmake not found (brew install cmake ninja / apt install cmake ninja-build)"; exit 1; }
command -v ninja >/dev/null || { echo "ninja not found (brew install ninja / apt install ninja-build)"; exit 1; }

mkdir -p "$CACHE/downloads" "$CACHE/src"
TARBALL="$CACHE/downloads/opencv-$VERSION.tar.gz"
if [ ! -f "$TARBALL" ] || ! echo "$SHA256  $TARBALL" | shasum -a 256 -c - >/dev/null 2>&1; then
  echo "Downloading OpenCV $VERSION sources"
  curl -sSL -o "$TARBALL" "https://github.com/opencv/opencv/archive/refs/tags/$VERSION.tar.gz"
  echo "$SHA256  $TARBALL" | shasum -a 256 -c -
fi

SRC="$CACHE/src/opencv-$VERSION"
BUILD="$CACHE/src/build-$VERSION-$OS-$ARCH"
rm -rf "$SRC" "$BUILD"
tar -xzf "$TARBALL" -C "$CACHE/src"
mkdir -p "$BUILD"

cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DBUILD_LIST=core,imgproc \
  -DBUILD_SHARED_LIBS=OFF \
  -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_opencv_apps=OFF \
  -DBUILD_JAVA=OFF -DBUILD_opencv_python3=OFF -DBUILD_opencv_python2=OFF -DBUILD_DOCS=OFF \
  -DWITH_IPP=OFF -DWITH_OPENCL=OFF -DWITH_CUDA=OFF -DWITH_FFMPEG=OFF -DWITH_GTK=OFF -DWITH_QT=OFF \
  -DWITH_V4L=OFF -DWITH_GSTREAMER=OFF -DWITH_OPENEXR=OFF -DWITH_JPEG=OFF -DWITH_PNG=OFF \
  -DWITH_TIFF=OFF -DWITH_WEBP=OFF -DWITH_OPENJPEG=OFF -DWITH_JASPER=OFF -DWITH_PROTOBUF=OFF \
  -DWITH_ITT=OFF -DWITH_LAPACK=OFF -DWITH_EIGEN=OFF -DWITH_TBB=OFF -DWITH_OPENMP=OFF \
  -DWITH_ADE=OFF -DWITH_1394=OFF -DWITH_AVFOUNDATION=OFF -DWITH_OBSENSOR=OFF \
  -DOPENCV_GENERATE_PKGCONFIG=OFF -DCV_TRACE=OFF -DOPENCV_ENABLE_NONFREE=OFF
cmake --build "$BUILD" --parallel
cmake --install "$BUILD"
rm -rf "$SRC" "$BUILD"
echo "OpenCV $VERSION installed at $PREFIX"
