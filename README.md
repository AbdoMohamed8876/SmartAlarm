# ⏰ Smart Alarm - المنبه الذكي

تطبيق منبه ذكي يستخدم الذكاء الاصطناعي للتحقق من أنك قمت من السرير فعلاً!

## 🎯 كيف يعمل؟
1. تضبط المنبه على وقت معين
2. تصوّر حاجة بعيدة عن غرفة النوم (مثلاً التلفزيون في الصالة)
3. لما المنبه يرن، لازم تفتح شاشة القفل وتروح تصوّر التلفزيون
4. الذكاء الاصطناعي يتحقق من الصورة → المنبه يوقف ✅

---

## 📲 خطوات رفع الكود وبناء الـ APK

### الخطوة 1: تثبيت Termux (من F-Droid)
```bash
# بعد فتح Termux
pkg update && pkg upgrade -y
pkg install git -y
```

### الخطوة 2: إعداد Git مع GitHub
```bash
git config --global user.name "اسمك"
git config --global user.email "ايميلك@gmail.com"
```

### الخطوة 3: إنشاء Repository على GitHub
1. افتح github.com من المتصفح
2. اضغط **New Repository**
3. اسمه: `SmartAlarm`
4. اتركه **Public**
5. اضغط **Create repository**
6. انسخ رابط الـ repo (مثال: `https://github.com/USERNAME/SmartAlarm.git`)

### الخطوة 4: رفع الكود من Termux
```bash
# انقل ملفات المشروع لـ Termux storage
# إذا حملت المشروع كـ zip افتحه أولاً

cd ~
git clone https://github.com/USERNAME/SmartAlarm.git MyAlarm
cd MyAlarm

# أو إذا عندك الملفات بالفعل:
cd /path/to/SmartAlarm
git init
git add .
git commit -m "Initial commit - Smart Alarm"
git branch -M main
git remote add origin https://github.com/USERNAME/SmartAlarm.git
git push -u origin main
```

**لما يطلب كلمة سر GitHub:**
- Username: اسم المستخدم
- Password: مش كلمة سر GitHub! لازم **Personal Access Token**
  - روح: github.com > Settings > Developer Settings > Personal Access Tokens > Tokens (classic)
  - اضغط Generate new token
  - خلي Expiration: 90 days
  - tick على `repo`
  - اضغط Generate واستخدمه كـ Password

### الخطوة 5: تشغيل GitHub Actions
1. افتح الـ repository على GitHub
2. اضغط تبويب **Actions**
3. هيظهر الـ workflow اسمه **Build Smart Alarm APK**
4. اضغط عليه واضغط **Run workflow**
5. انتظر 5-10 دقايق
6. بعد ما يخلص، اضغط على الـ build واضغط **Artifacts**
7. حمّل **SmartAlarm-Debug**

### الخطوة 6: تثبيت الـ APK
1. افضل المتصفح وانزّل ملف الـ APK
2. افتحه → اضغط **تثبيت**
3. إذا طلب "مصادر غير معروفة": الإعدادات > الأمان > مصادر غير معروفة ✅

---

## 🔑 إعداد Gemini API (مجاني)
1. افتح: **aistudio.google.com**
2. سجل دخول بـ Google
3. اضغط **Get API Key** > **Create API key**
4. انسخ المفتاح
5. افتح التطبيق > ⚙️ الإعدادات > الصق المفتاح > حفظ

---

## 📱 Samsung Galaxy A13 - ملاحظة
- الـ APK يدعم 32-bit وهيشتغل بشكل مثالي
- minSdk 24 = Android 7.0 فأعلى ✅

---

## 🔧 المتطلبات
- Android 7.0 (API 24) أو أعلى
- إنترنت (للتحقق بالذكاء الاصطناعي)
- كاميرا خلفية

---

## 📂 هيكل المشروع
```
SmartAlarm/
├── app/src/main/
│   ├── java/com/smartalarm/
│   │   ├── ai/           ← Gemini AI للتحقق من الصور
│   │   ├── data/         ← نماذج البيانات والتخزين
│   │   ├── receiver/     ← استقبال المنبه وإعادة التشغيل بعد الإقلاع
│   │   ├── service/      ← خدمة الرنين الخلفية
│   │   ├── ui/           ← جميع الشاشات
│   │   └── utils/        ← أدوات مساعدة
│   └── res/              ← التصاميم والموارد
└── .github/workflows/    ← GitHub Actions للبناء التلقائي
```
