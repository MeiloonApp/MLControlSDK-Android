package com.meiloon.mlcontrolcore_aos.ota

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * 負責：分包 (Slicing)、隊列管理、等待 Callback、失敗重試
 */
class BleDataSender(
    private val scope: CoroutineScope,
    private val getMtu: () -> Int,
    private val getGatt: () -> BluetoothGatt?,
    private val getWriteChar: () -> BluetoothGattCharacteristic?
) {
    private val TAG = "BleDataSender"
    private var sendQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val resultChannel = Channel<Int>(Channel.CONFLATED)
    private var processingJob: Job? = null

    init {
        startProcessing()
    }

    /**
     * 對應 Jieli 的 addSendTask: 處理分包並放入隊列
     */
    fun enqueue(data: ByteArray) {
        Log.d(TAG, "Enqueue data: ${com.jieli.bluetooth.utils.CHexConver.byte2HexStr(data)}")
        val mtu = getMtu()
        // BLE MTU payload 預設為 MTU - 3
        val payloadSize = if (mtu > 3) mtu - 3 else 20

        var offset = 0
        while (offset < data.size) {
            val size = Math.min(data.size - offset, payloadSize)
            val chunk = data.copyOfRange(offset, offset + size)
            val result = sendQueue.trySend(chunk)
            if (result.isFailure) {
                Log.e(TAG, "Failed to enqueue data chunk")
            }
            offset += size
        }
    }

    /**
     * 啟動處理循環 (對應 Thread 的 run)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private fun startProcessing() {
        processingJob?.cancel()
        processingJob = scope.launch(Dispatchers.IO) {
            try {
                for (data in sendQueue) {
                    var retryNum = 0
                    var success = false

                    while (retryNum < 3 && !success && isActive) {
                        val gatt = getGatt()
                        val char = getWriteChar()

                        if (gatt == null || char == null) {
                            Log.e(TAG, "Gatt or Characteristic is null, waiting...")
                            delay(500)
                            retryNum++
                            continue
                        }

                        // 設置資料並發送
                        char.value = data
                        // Jieli OTA 建議使用 NO_RESPONSE
                        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                        val writeInitiated = gatt.writeCharacteristic(char)

                        if (writeInitiated) {
                            // 等待 onCharacteristicWrite 的回傳結果，超時設定為 2 秒 (對應 SEND_DATA_MAX_TIMEOUT)
                            val status = withTimeoutOrNull(2000) {
                                resultChannel.receive()
                            }

                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                success = true
                            } else {
                                Log.w(TAG, "Write callback status error: $status, retry: ${retryNum + 1}")
                                retryNum++
                                delay(50) // 稍微延遲再重試
                            }
                        } else {
                            Log.w(TAG, "writeCharacteristic returned false, retry: ${retryNum + 1}")
                            retryNum++
                            delay(100)
                        }
                    }

                    if (!success && isActive) {
                        Log.e(TAG, "Data packet failed after 3 retries, clearing queue.")
                        clearQueue()
                        // 通知失敗可以在此擴充 callback
                    }
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Processing job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in processing loop: ${e.message}")
            }
        }
    }

    /**
     * 當 onCharacteristicWrite 收到回報時呼叫 (對應 wakeupSendThread)
     */
    fun onWriteResult(status: Int) {
        resultChannel.trySend(status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearQueue() {
        processingJob?.cancel()
        sendQueue.close()
        sendQueue = Channel(Channel.UNLIMITED)
        startProcessing()
    }

    fun release() {
        processingJob?.cancel()
        sendQueue.close()
        resultChannel.close()
    }
}
