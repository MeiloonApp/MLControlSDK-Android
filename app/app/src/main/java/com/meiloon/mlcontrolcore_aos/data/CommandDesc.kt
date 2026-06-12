package com.meiloon.mlcontrolcore_aos.data

import com.meiloon.controlcore.main.api.enums.CommandType

class CommandDesc(var type: String, var desc: String, var demo: String) {
    companion object {
        fun getDescData(commandType: CommandType): CommandDesc {
            println("當前的 command 是: $commandType")

            return when (commandType) {
                CommandType.GetAllEQPara -> {
                    CommandDesc(
                        "case GetAllEQPara",
                        "同步所有通道參數",
                        "BleControlManager.getInstance().getAllEQPara(address)"
                    )
                }

                CommandType.GetAudioBand -> {
                    CommandDesc(
                        "case GetAudioBand",
                        "查詢每通道支援段數",
                        "BleControlManager.getInstance().getAudioBand(address)"
                    )
                }

                CommandType.GetAudioChannel -> {
                    CommandDesc(
                        "case GetAudioChannel",
                        "查詢音軌數量",
                        "BleControlManager.getInstance().getAudioChannel(address)"
                    )
                }

                CommandType.GetAudioChipID -> {
                    CommandDesc(
                        "case GetAudioChipID",
                        "取得晶片唯一識別碼(SPA101:01, JL:02, ESP:03)",
                        "BleControlManager.getInstance().getAudioChipID(address)"
                    )
                }

                CommandType.GetAudioChipNumbers -> {
                    CommandDesc(
                        "case GetAudioChipNumbers",
                        "取得音訊處理晶片數量",
                        "BleControlManager.getInstance().getAudioChipNumbers(address)"
                    )
                }

                CommandType.GetAudioSampleRate -> {
                    CommandDesc(
                        "case GetAudioSampleRate",
                        "查詢音訊取樣率",
                        "BleControlManager.getInstance().getAudioSampleRate(address)"
                    )
                }

                CommandType.GetBTPairing -> {
                    CommandDesc(
                        "case GetBTPairing",
                        "查詢當前是否在配對中",
                        "BleControlManager.getInstance().BTPairing(address)"
                    )
                }

                CommandType.GetBattery -> {
                    CommandDesc(
                        "case GetBattery",
                        "取得剩餘電量百分比(0-100)",
                        "BleControlManager.getInstance().getBattery(address)"
                    )
                }

                CommandType.GetChipID -> {
                    CommandDesc(
                        "case GetChipID",
                        "取得晶片識別碼(SPA101: 0001, JL: 00002, ESP: 0003)",
                        "BleControlManager.getInstance().getChipID(address)"
                    )
                }

                CommandType.GetEQEngine -> {
                    CommandDesc(
                        "case GetEQEngine",
                        "查詢EQ引擎是否啟用",
                        "BleControlManager.getInstance().getEQEngine(address)"
                    )
                }
                CommandType.GetEQGroup -> {
                    CommandDesc(
                        "case GetEQGroup",
                        "查詢EQ分組是否啟用",
                        "BleControlManager.getInstance().getEQGroup(address)"
                    )
                }
                CommandType.GetEQMode -> {
                    CommandDesc(
                        "case GetEQMode",
                        "取得LFEQ, HFEQ, DESKEQ",
                        "BleControlManager.getInstance().getEQMode(address)"
                    )
                }

                CommandType.GetEQRange -> {
                    CommandDesc(
                        "case GetEQRange",
                        "取得Freq/Gain/Q極限",
                        "BleControlManager.getInstance().getEQRange(address)"
                    )
                }

                CommandType.GetFirmwareVer -> {
                    CommandDesc(
                        "case GetFirmwareVer",
                        "取得當前燒錄的軟體版本號",
                        "BleControlManager.getInstance().getFirmwareVer(address)"
                    )
                }

                CommandType.GetRoomCorrectionMode -> {
                    CommandDesc(
                        "case GetRoomCorrectionMode",
                        "0:Normal, 1:Tuning",
                        "BleControlManager.getInstance().getRoomCorrectionMode(address)"
                    )
                }

                CommandType.GetVolume -> {
                    CommandDesc(
                        "case GetVolume",
                        "查詢當前主音量",
                        "BleControlManager.getInstance().getVolume(address)"
                    )
                }

                CommandType.GetMute -> {
                    CommandDesc(
                        "case GetMute",
                        "查詢當前是否處於靜音狀態",
                        "BleControlManager.getInstance().getMute(address)"
                    )
                }

                is CommandType.SetBTDeviceName -> {
                    CommandDesc(
                        "case SetBTDeviceName",
                        "修改設備的藍牙廣播名稱",
                        "BleControlManager.getInstance().setBtDeviceName(address, value)"
                    )
                }

                is CommandType.SetDeskEQ -> {
                    CommandDesc(
                        "case SetDeskEQ",
                        "開啟或關閉桌面補償",
                        "BleControlManager.getInstance().setDeskEQ(address, value)"
                    )
                }

                is CommandType.SetEQEngine -> {
                    CommandDesc(
                        "case SetEQEngine",
                        "設定EQ引擎(0:Off, 1:On)",
                        "BleControlManager.getInstance().setEQEngine(address, value)"
                    )
                }
                is CommandType.SetEQGroup -> {
                    CommandDesc(
                        "case SetEQGroup",
                        "設定EQ分組(0:Off, 1:On)",
                        "BleControlManager.getInstance().setEQGroup(address, value)"
                    )
                }
                is CommandType.SetEQPara -> {
                    CommandDesc(
                        "case SetEQPara",
                        "設定單段EQ參數",
                        "BleControlManager.getInstance().sendEQPara(address, chipIndex, channelIndex, eqData)"
                    )
                }
                is CommandType.SetHFEQ -> {
                    CommandDesc(
                        "case SetHFEQ",
                        "開啟或關閉高頻增益",
                        "BleControlManager.getInstance().setHFEQ(address, value)"
                    )
                }

                is CommandType.SetLFEQ -> {
                    CommandDesc(
                        "case SetLFEQ",
                        "開啟或關閉低頻增益",
                        "BleControlManager.getInstance().setLFEQ(address, value)"
                    )
                }

                is CommandType.SetLastVolume -> {
                    CommandDesc(
                        "case SetLastVolume",
                        "設定儲存的最後音量值",
                        "BleControlManager.getInstance().setLastVolume(address, value)"
                    )
                }

                is CommandType.SetRoomCorrectionMode -> {
                    CommandDesc(
                        "case SetRoomCorrectionMode",
                        "設定房間校正",
                        "BleControlManager.getInstance().setRoomCorrectionMode(address, value)"
                    )
                }

                is CommandType.SetSPKMute -> {
                    CommandDesc(
                        "case SetSPKMute",
                        "開啟或關閉揚聲器輸出",
                        "BleControlManager.getInstance().setSPKMute(address, value)"
                    )
                }

                is CommandType.SetVolume -> {
                    CommandDesc(
                        "case SetVolume",
                        "設定前主音量(SPA:0-15, JL:0-46, ESP: 0-100)",
                        "BleControlManager.getInstance().setVolume(address, value)"
                    )
                }

                is CommandType.SetAllEQPara -> {
                    CommandDesc(
                        "case SetAllEQPara",
                        "設定所有通道參數",
                        "BleControlManager.getInstance().sendAllEQPara(address, chipIndex, channelIndex, eqDataList)"
                    )
                }

                is CommandType.GetEQPara -> {
                    CommandDesc(
                        "case GetEQPara",
                        "取得單筆PEQ資訊",
                        "BleControlManager.getInstance().getEQPara(address, chipIndex, channel, bandIndex)"
                    )
                }

                is CommandType.GetChannelEQPara -> {
                    CommandDesc(
                        "case GetChannelEQPara",
                        "讀取指定通道全部參數",
                        "BleControlManager.getInstance().sendAllEQPara(address, chipIndex, channelIndex)"
                    )
                }

                is CommandType.SaveEQPara -> {
                    CommandDesc(
                        "case SaveEQPara",
                        "寫入PEQ到設備",
                        "BleControlManager.getInstance().saveEQPara(address)"
                    )
                }

                CommandType.StartBTPairing -> {
                    CommandDesc(
                        "case StartBTPairing",
                        "讓設備進入藍牙配對狀態",
                        "BleControlManager.getInstance().startBTPairing(address)"
                    )
                }

                CommandType.GetStatus -> {
                    CommandDesc(
                        "case GetStatus",
                        "取得設備當前模式、輸入源等綜合資訊",
                        "BleControlManager.getInstance().getStatus(address)"
                    )
                }
            }
        }
    }
}