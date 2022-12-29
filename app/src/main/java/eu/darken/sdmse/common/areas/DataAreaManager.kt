package eu.darken.sdmse.common.areas

import eu.darken.sdmse.common.areas.modules.DataAreaFactory
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.randomString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DataAreaManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val areaFactory: DataAreaFactory,
) {

    private val refreshTrigger = MutableStateFlow(randomString())
    private val _internalStateCache = MutableStateFlow<State?>(null)
    val latestState: Flow<State?> = _internalStateCache

    val state: Flow<State> = refreshTrigger
        .mapLatest {
            State(
                areas = areaFactory.build().toSet(),
            )
        }
        .onEach { _internalStateCache.value = it }
        .setupCommonEventHandlers(TAG) { "state" }
        .shareIn(appScope, SharingStarted.Lazily, 1)

    suspend fun reload() {
        log(TAG, WARN) { "reload()" }
        refreshTrigger.value = randomString()
    }

    data class State(
        val areas: Set<DataArea>,
    )

    companion object {
        val TAG: String = logTag("DataArea", "Manager")
    }
}