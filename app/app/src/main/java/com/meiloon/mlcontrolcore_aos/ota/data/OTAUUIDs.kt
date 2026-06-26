package com.meiloon.mlcontrolcore_aos.ota.data

import com.meiloon.controlcore.main.api.bluetooth.BTConfig
import java.util.UUID

/**
 * 杰理 OTA 相關的 UUID 封裝
 */
object OTAUUIDs {
    val SERVICE: UUID by lazy { UUID.fromString(BTConfig.jieLiServiceUUID) }
    val WRITE: UUID by lazy { UUID.fromString(BTConfig.jieLiWriteUUID) }
    val NOTIFY: UUID by lazy { UUID.fromString(BTConfig.jieLiNotifyUUID) }
    // 標準 BLE Descriptor UUID (Client Characteristic Configuration)
    val DESC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}