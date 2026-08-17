package com.feverdestiny.miaotv.data.entities

import androidx.compose.runtime.Immutable
import com.feverdestiny.miaotv.data.entities.Epg.Companion.currentProgrammes

@Immutable
data class EpgList(
    val value: List<Epg> = emptyList(),
) : List<Epg> by value {
    companion object {
        /**
         * 当前节目/下一个节目
         */
        fun EpgList.currentProgrammes(iptv: Iptv): EpgProgrammeCurrent? {
            return firstOrNull { it.matchesIptv(iptv) }?.currentProgrammes()
        }
    }
}