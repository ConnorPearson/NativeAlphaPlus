package com.cylonid.nativealpha.helper

import android.util.Log
import com.cylonid.nativealpha.model.AdblockConfig
import com.cylonid.nativealpha.util.DateUtils
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.Filter

internal class AdblockProviderApiHelper(private val adFilterProvider: AdFilter) {

    private var lastSyncHash: Int = 0

    fun synchronizeAdblockProviderWithSettings(settings: List<AdblockConfig>) {
        val currentHash = settings.hashCode()
        if (currentHash == lastSyncHash) return
        lastSyncHash = currentHash

        try {
            val filtersValue = adFilterProvider.viewModel.filters.value
            val map = transformToMapWithUrlKey(filtersValue ?: emptyMap())
            for (config: AdblockConfig in settings) {
                var setFilter = map[config.value]
                if (setFilter == null) {
                    Log.d("NativeAlpha", "Adding new adblock filter: ${config.label}")
                    setFilter = adFilterProvider.viewModel.addFilter(config.label, config.value)
                    adFilterProvider.viewModel.download(setFilter.id)
                } else if (DateUtils.isOlderThanDays(setFilter.updateTime, 30)) {
                    Log.d("NativeAlpha", "Updating adblock filter: ${config.label}")
                    adFilterProvider.viewModel.download(setFilter.id)
                }
            }
            for ((_, filter) in map) {
                val existingConfig = settings.find { it.value == filter.url }
                if(existingConfig == null) {
                    adFilterProvider.viewModel.removeFilter(filter.id)
                }
            }
        } catch (e: Exception) {
            Log.e("NativeAlpha", "Error synchronizing adblock settings", e)
        }
    }

    private fun transformToMapWithUrlKey(originalMap: Map<String, Filter>): Map<String, Filter> {
        val urlBasedMap: HashMap<String, Filter> = HashMap()
        for ((_, value) in originalMap) {
            urlBasedMap[value.url] = value
        }
        return urlBasedMap
    }
}