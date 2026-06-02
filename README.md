# MLControlSDK (Android)

Meiloon 藍牙控制與音訊處理 SDK，專為 Android 平台設計，提供完整的 BLE 通訊協議、PEQ 音訊演算法引擎與空間校正 (Room Correction) 運算能力。

> 📖 **[點此查看完整 API 說明文件 (Dokka)](https://meiloonapp.github.io/MLControlSDK-Android/)**

## 🚀 安裝方式

目前 SDK 採本地 AAR 包發布，請按照以下步驟整合至您的 Android 專案：

### 1. 匯入二進位包
將本專案 `aar_package/` 目錄下的所有 `.aar` 檔案（包含 `ControlCore-release.aar` 以及所有 `jl_*.aar` 支持庫），複製到您 Android 專案的 `app/libs/` 目錄下（若無則新建）。

### 2. 設定 build.gradle.kts
在您的 App 模組的 `build.gradle.kts` 中，加入以下設定：

```kotlin
dependencies {
    // 引入 libs 資料夾下的所有 AAR
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    
    // SDK 核心依賴
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("com.polidea.rxandroidble3:rxandroidble:1.17.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

---

## ⚙️ 權限設定 (Permissions)

請確保您的 App `AndroidManifest.xml` 中宣告了以下權限：

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<!-- 空間校正與電平檢測必備權限 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

---

## 🔑 驗證與初始化 SDK

SDK 所有藍牙控制與音訊運算功能均受金鑰保護。您必須在 App 啟動時完成授權。

### 方式 1：使用設定檔 (推薦)
在您專案的 `src/main/assets/` 目錄下建立 `MLKey.txt`，寫入從官方取得的 API Key。
隨後在 Application 或 MainActivity 中呼叫：

```kotlin
import com.meiloon.controlcore.MLControlCore

MLControlCore.getInstance().configure(context) { success, error ->
    if (success) {
        println("SDK 授權成功！")
    } else {
        println("授權失敗: $error")
    }
}
```

### 方式 2：程式碼傳入金鑰
```kotlin
MLControlCore.getInstance().configure(context, "YOUR-API-KEY") { success, error ->
    // ...
}
```

---

## 📱 核心功能使用

### 1. 藍牙掃描與連線
透過 `BleManager` 進行掃描，再由 `BleControlManager` 發起連線：

```kotlin
import com.meiloon.controlcore.widget.app.ble.BleManager
import com.meiloon.controlcore.main.widget.ble.BleControlManager

// 掃描
BleManager.getInstance().startScan({ isScanning ->
    // 掃描狀態
}, 30).subscribe({ result ->
    val device = BluetoothEntity(result)
    
    // 連線 (false 代表使用一般藍牙而非 MQTT)
    BleControlManager.getInstance().connect(device, false)
}, { error -> })
```

### 2. 空間校正 (Room Correction)
包含環境電平檢測、錄音與 C++ 擬合演算法。詳細配置可參考 `MLRoomCorrectionOptions`。

```kotlin
val engine = MLRoomCorrectionEngine.getInstance()

// 錄製遠場音訊
engine.recordMeasurement(MLRoomMeasurementType.FAR_FIELD) { ffFile ->
    val options = MLRoomCorrectionOptions(numBands = 15, enableHouseCurve = true)
    
    // 執行擬合演算法
    engine.analyzeFiles(nfFile, ffFile, options) { result, error ->
        result?.peqBands?.forEach { band ->
            println("Band: Freq=${band.freq}Hz, Gain=${band.gain}dB")
        }
    }
}
```

### 3. 指令與狀態接收
SDK 採用 `EventBus` 進行全域狀態廣播。

```kotlin
// 註冊 EventBus
EventBus.getDefault().register(this)

@Subscribe(threadMode = ThreadMode.MAIN)
fun onReceiveCommand(event: ReceiveCommandEvent) {
    // 處理設備回傳的資料
}
```

## 🔐 隱私聲明
本 SDK 已透過底層 C++ (JNI) 實作多層加密防護，核心演算法與傳輸過程皆具備商業機密保護層級。
