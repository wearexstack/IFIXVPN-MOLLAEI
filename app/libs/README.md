# هسته Xray برای IFIX VPN

مسیر پیش‌فرض اپ **Xray-core** است (نه sing-box).

## گزینه ۱ — libv2ray (AndroidLibXrayLite)

1. از پروژه [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) یا فورک سازگار، AAR بسازید.
2. فایل را اینجا کپی کنید:

```text
app/libs/libv2ray.aar
```

3. پروژه را دوباره بیلد کنید. `build.gradle.kts` همه `*.aar` این پوشه را لود می‌کند.

## گزینه ۲ — باینری xray

باینری رسمی را برای ABI دستگاه در `jniLibs` بگذارید، مثلاً:

```text
app/src/main/jniLibs/arm64-v8a/libxray.so
```

`XrayEngine` مسیر `nativeLibraryDir` را برای `libxray.so` / `xray` چک می‌کند.

## پروتکل‌های پشتیبانی‌شده در کانفیگ

| لینک | Xray |
|------|------|
| vless:// | ✅ |
| trojan:// | ✅ |
| vmess:// | ✅ |
| ss:// | ✅ |
| hysteria2:// | ❌ (نیاز به sing-box) |

## لاگ

```bash
adb logcat -s XrayEngine:D IfixVpnService:D
```

## نکته

تونل سیستم (`VpnService`) ترافیک را می‌گیرد؛ برای مسیر کامل TUN→SOCKS→Xray در نسخه‌های بعدی می‌توان **tun2socks / hev-socks5-tunnel** اضافه کرد. با `addDisallowedApplication(packageName)` هسته Xray می‌تواند به نود وصل شود.
