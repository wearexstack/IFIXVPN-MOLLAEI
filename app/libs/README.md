# libv2ray.aar (الزامی برای اتصال واقعی)

بدون این فایل، اپ بیلد می‌شود ولی **پروکسی واقعی کار نمی‌کند**.

## ساخت از AndroidLibXrayLite

```bash
git clone https://github.com/2dust/AndroidLibXrayLite.git
cd AndroidLibXrayLite
gomobile init
go mod tidy -v
gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid= -checklinkname=0' ./
# خروجی: libv2ray.aar + libv2ray-sources.jar
cp libv2ray.aar  /path/to/IFIXVPN-MOLLAEI/app/libs/
```

سپس در ریشه پروژه:

```bash
gradle :app:assembleDebug
```

API استفاده‌شده در اپ:

- `Libv2ray.InitCoreEnv(path, key)`
- `Libv2ray.NewCoreController(CoreCallbackHandler)`
- `CoreController.StartLoop(configJson, tunFd)`
- `CoreController.StopLoop()`
