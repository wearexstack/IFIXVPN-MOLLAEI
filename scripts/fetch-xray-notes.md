# Notes: obtaining Xray for Android

## Build libv2ray (Go + gomobile)

```bash
git clone https://github.com/2dust/AndroidLibXrayLite.git
cd AndroidLibXrayLite
# follow upstream README (gomobile bind)
# copy resulting .aar to IFIXVPN-MOLLAEI/app/libs/libv2ray.aar
```

## Official Xray releases

https://github.com/XTLS/Xray-core/releases

Android is not always published as a ready `.so`; many apps build via AndroidLibXrayLite.

## After placing the AAR

```bash
gradle :app:assembleDebug
```
