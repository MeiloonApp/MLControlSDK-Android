package com.meiloon.mlcontrolcore_aos.data

import com.meiloon.controlcore.main.api.EQMode
import com.meiloon.controlcore.main.api.enums.CommandType.*
import com.meiloon.controlcore.main.api.enums.CommandType.GetAllEQPara
import com.meiloon.controlcore.main.api.enums.CommandType.GetAudioBand
import com.meiloon.controlcore.main.api.enums.CommandType.GetAudioChannel
import com.meiloon.controlcore.main.api.enums.CommandType.GetAudioChipNumbers
import com.meiloon.controlcore.main.api.enums.CommandType.GetAudioSampleRate
import com.meiloon.controlcore.main.api.enums.CommandType.GetBTPairing
import com.meiloon.controlcore.main.api.enums.CommandType.GetBattery
import com.meiloon.controlcore.main.api.enums.CommandType.GetChipID
import com.meiloon.controlcore.main.api.enums.CommandType.GetEQEngine
import com.meiloon.controlcore.main.api.enums.CommandType.GetEQGroup
import com.meiloon.controlcore.main.api.enums.CommandType.GetEQRange
import com.meiloon.controlcore.main.api.enums.CommandType.GetFirmwareVer
import com.meiloon.controlcore.main.api.enums.CommandType.GetRoomCorrectionMode
import com.meiloon.controlcore.main.api.enums.CommandType.GetVolume
import com.meiloon.controlcore.main.api.enums.CommandType.SetAllEQPara
import com.meiloon.controlcore.main.api.enums.CommandType.SetBTDeviceName
import com.meiloon.controlcore.main.api.enums.CommandType.SetDeskEQ
import com.meiloon.controlcore.main.api.enums.CommandType.SetEQPara
import com.meiloon.controlcore.main.api.enums.CommandType.SetHFEQ
import com.meiloon.controlcore.main.api.enums.CommandType.SetLFEQ
import com.meiloon.controlcore.main.api.enums.CommandType.SetLastVolume
import com.meiloon.controlcore.main.api.enums.CommandType.SetRoomCorrectionMode
import com.meiloon.controlcore.main.api.enums.CommandType.SetVolume
import com.meiloon.controlcore.main.api.enums.CommandType.StartBTPairing

class Command {
    companion object {
        val items = arrayOf(
            CommandItem("[通用] 取得晶片 ID", GetChipID),
            CommandItem("[通用] 取得韌體版本", GetFirmwareVer),
            CommandItem("[通用] 取得電量", GetBattery),
            CommandItem("[通用] 設定藍牙名稱", SetBTDeviceName()),

            CommandItem("[音量狀態] 取得主音量", GetVolume),
            CommandItem("[音量狀態] 設定主音量", SetVolume()),
            CommandItem("[音量狀態] 設定最後音量", SetLastVolume()),
            CommandItem("[音量狀態] 取得靜音狀態", GetMute),
            CommandItem("[音量狀態] 設定靜音狀態", SetSPKMute()),

            CommandItem("[Monitor 專屬] 設定Desk EQ", SetDeskEQ()),
            CommandItem("[Monitor 專屬] 設定HF EQ", SetHFEQ()),
            CommandItem("[Monitor 專屬] 設定LF EQ", SetLFEQ()),
            CommandItem("[Jieli 專屬] 取得 EQ 模式", GetEQMode),

            CommandItem("[Jieli 專屬] 取得房間校正模式", GetRoomCorrectionMode),
            CommandItem("[Jieli 專屬] 設定房間校正模式", SetRoomCorrectionMode()),

            CommandItem("[Monitor 專屬] 藍牙配對狀態", GetBTPairing),
            CommandItem("[Monitor 專屬] 開始藍牙配對", StartBTPairing),

            CommandItem("[PEQ 進階] 取得晶片總數", GetAudioChipNumbers),
            CommandItem("[PEQ 進階] 取得通道數", GetAudioChannel),
            CommandItem("[PEQ 進階] 取得取樣率", GetAudioSampleRate),
            CommandItem("[PEQ 進階] 取得 PEQ 段數", GetAudioBand),
            CommandItem("[PEQ 進階] 取得 EQ 範圍", GetEQRange),

            CommandItem("[PEQ 進階] 讀取所有 PEQ", GetAllEQPara),

            CommandItem("[PEQ 進階] 取得 EQ 分組狀態", GetEQGroup),
            CommandItem("[PEQ 進階] 設定 EQ 分組狀態", SetEQGroup()),
            CommandItem("[PEQ 進階] 取得 EQ 引擎狀態", GetEQEngine),
            CommandItem("[PEQ 進階] 設定 EQ 引擎狀態", SetEQEngine()),

            CommandItem("[PEQ 進階] 取得指定PEQ參數", GetEQPara()),
            CommandItem("[PEQ 進階] 取得通道所有PEQ", GetChannelEQPara()),

            CommandItem("[音量狀態] 取得綜合狀態", GetStatus),
//            CommandItem("[PEQ 進階] 設定所有 PEQ", SetAllEQPara()),
//            CommandItem("[音量狀態] 設定靜音開關", APIMethod.UNKNOWN),
//            CommandItem("[音量狀態] 取得藍牙源音量", APIMethod.BTVol),
//            CommandItem("[音量狀態] 設定藍牙源音量", APIMethod.BTVol),
//            CommandItem("[音量狀態] 取得 UAC 音量", APIMethod.UACVol),
//            CommandItem("[音量狀態] 設定 UAC 音量", APIMethod.UACVol),

//            CommandItem("[PEQ 進階] 讀取 PEQ", APIMethod.PreEQMode),

//            CommandItem("[Subwoofer] 設定分頻點", APIMethod.CrossOver),
//            CommandItem("[Subwoofer] 讀取分頻點", APIMethod.CrossOver),
//            CommandItem("[Subwoofer] 設定相位值", APIMethod.Phase),
//            CommandItem("[Subwoofer] 讀取相位值", APIMethod.Phase),

//            CommandItem("[EP32] 取得設備IP", APIMethod.GetDeviceIP),
//            CommandItem("[EP32] 取得MP3狀態", APIMethod.GetMP3PlayerStatus)
        )

        fun getDisplayNames(): Array<String> {
            return items.map { it.name }.toTypedArray()
        }

    }
}