#!/usr/bin/env python3
"""Build the dependency-free personal APK using an installed Android SDK and JDK 17."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
args = parser.parse_args()
sdk = Path(args.sdk)
if not args.sdk or not (sdk / "platforms/android-35/android.jar").is_file():
    parser.error("Install Android SDK platform 35 and build-tools 35.0.0, then pass --sdk PATH.")
root = Path(__file__).resolve().parent.parent
build = root / "app/build/manual"
if build.exists():
    shutil.rmtree(build)
build.mkdir(parents=True)
tools = sdk / "build-tools/35.0.0"
android = sdk / "platforms/android-35/android.jar"
def run(*command):
    subprocess.run([str(x) for x in command], check=True)
run(tools / "aapt2", "compile", "--dir", root / "app/src/main/res", "-o", build / "resources.zip")
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml")
manifest.getroot().set("package", "app.blocker")
manifest.write(build / "AndroidManifest.xml")
run(tools / "aapt2", "link", "-o", build / "base.apk", "--manifest", build / "AndroidManifest.xml",
    "-I", android, "--java", build / "generated", "--min-sdk-version", "26", "--target-sdk-version", "35",
    "--version-code", "10", "--version-name", "5.4.1", "-A", root / "app/src/main/assets", build / "resources.zip")
classes = build / "classes"
classes.mkdir()
sources = list((root / "app/src/main/java").rglob("*.java")) + list((build / "generated").rglob("*.java"))
run("java", "com.sun.tools.javac.Main", "--release", "17", "-classpath", android, "-d", classes, *sources)
dex = build / "dex"
dex.mkdir()
run(tools / "d8", "--lib", android, "--min-api", "26", "--output", dex, *classes.rglob("*.class"))
shutil.copyfile(build / "base.apk", build / "unsigned.apk")
run("java", "sun.tools.jar.Main", "uf", build / "unsigned.apk", "-C", dex, "classes.dex")
run(tools / "zipalign", "-p", "-f", "4", build / "unsigned.apk", build / "aligned.apk")
key = root / "signing/personal-debug.keystore"
key.parent.mkdir(parents=True, exist_ok=True)
if not key.exists():
    run("keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
        "-alias", "androiddebugkey", "-dname", "CN=Android Debug,O=Android,C=US", "-keyalg", "RSA",
        "-keysize", "2048", "-validity", "10000")
output = root / "app/build/outputs/apk/personal"
output.mkdir(parents=True, exist_ok=True)
apk = output / "BLOCKER.apk"
run(tools / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android",
    "--out", apk, build / "aligned.apk")
run(tools / "apksigner", "verify", "--verbose", apk)
print(apk)
