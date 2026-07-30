# IFIX VPN

کلاینت اندروید اختصاصی **IFIX VPN** – رابط فارسی، لایسنس یک‌دستگاهی، لیست سرور از ساب گیت‌هاب، و `VpnService` سیستمی.

## امکانات

- فعال‌سازی لایسنس (۱۰ کلید یک‌ماهه در بک‌اند)
- بارگذاری سرور از `https://raw.githubusercontent.com/wearexstack/xstack/main/sub`
- پارس `vless` / `trojan` / `hysteria2` / `vmess` / `ss`
- صفحه اصلی با اتصال یک‌ضرب، انتخاب سرور، آمار زنده
- `IfixVpnService` با TUN و ساخت کانفیگ sing-box / Xray

## ساخت

```bash
gradle :app:assembleDebug
```

یا از GitHub Actions روی برنچ `main`.

## طراحی

تمام UI، پالت رنگ (ایندیگو/بنفش) و ساختار کد **اختصاصی IFIX** است و از هیچ اپ دیگری کپی نشده.

## هسته پروکسی

برای تونل کامل پروتکل روی همه ترافیک گوشی، هسته native (مثل libbox/sing-box) باید به TUN وصل شود. اسکلت سرویس و کانفیگ آماده است؛ در صورت نیاز هسته رسمی در مرحله بعد embed می‌شود.
