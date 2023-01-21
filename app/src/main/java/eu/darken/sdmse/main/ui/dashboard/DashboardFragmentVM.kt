package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.ui.AppCleanerCardVH
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderCardVH
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerDeleteTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerCardVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class DashboardFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val taskManager: TaskManager,
    private val setupManager: SetupManager,
    private val corpseFinder: CorpseFinder,
    private val systemCleaner: SystemCleaner,
    private val appCleaner: AppCleaner,
    private val debugCardProvider: DebugCardProvider,
    private val upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val refreshTrigger = MutableStateFlow(rngString)
    private var isSetupDismissed = false
    val dashboardevents = SingleLiveEvent<DashboardEvents>()

    init {
        if (!generalSettings.isOnboardingCompleted.valueBlocking) {
            DashboardFragmentDirections.actionDashboardFragmentToOnboardingFragment().navigate()
        }
    }

    private val corpseFinderItem: Flow<CorpseFinderCardVH.Item> = combine(
        corpseFinder.data,
        corpseFinder.progress,
    ) { data, progress ->
        CorpseFinderCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch {
                    taskManager.useRes {
                        taskManager.submit(CorpseFinderScanTask())
                    }
                }
            },
            onDelete = {
                launch { taskManager.submit(CorpseFinderDeleteTask()) }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.CORPSEFINDER) }
            },
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToCorpseFinderDetailsFragment().navigate()
            }
        )
    }
    private val systemCleanerItem: Flow<SystemCleanerCardVH.Item> = combine(
        systemCleaner.data,
        systemCleaner.progress,
    ) { data, progress ->
        SystemCleanerCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch {
                    taskManager.useRes {
                        taskManager.submit(SystemCleanerScanTask())
                    }
                }
            },
            onDelete = {
                launch { taskManager.submit(SystemCleanerDeleteTask()) }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.SYSTEMCLEANER) }
            },
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToSystemCleanerDetailsFragment().navigate()
            }
        )
    }
    private val appCleanerItem: Flow<AppCleanerCardVH.Item> = combine(
        appCleaner.data,
        appCleaner.progress,
    ) { data, progress ->
        AppCleanerCardVH.Item(
            data = data,
            progress = progress,
            onScan = {
                launch {
                    taskManager.useRes {
                        taskManager.submit(AppCleanerScanTask())
                    }
                }
            },
            onDelete = {
                launch { taskManager.submit(AppCleanerDeleteTask()) }
            },
            onCancel = {
                launch { taskManager.cancel(SDMTool.Type.APPCLEANER) }
            },
            onViewDetails = {
                DashboardFragmentDirections.actionDashboardFragmentToAppCleanerDetailsFragment().navigate()
            }
        )
    }
    private val dataAreaItem: Flow<DataAreaCardVH.Item?> = areaManager.latestState
        .map {
            if (it == null) return@map null
            if (it.areas.isNotEmpty()) return@map null
            DataAreaCardVH.Item(
                state = it,
                onReload = {
                    launch {
                        areaManager.reload()
                    }
                }
            )
        }

    val listItems: LiveData<List<DashboardAdapter.Item>> = eu.darken.sdmse.common.flow.combine(
        debugCardProvider.create(this),
        upgradeRepo.upgradeInfo.map { it }.onStart { emit(null) },
        setupManager.state,
        dataAreaItem,
        corpseFinderItem,
        systemCleanerItem,
        appCleanerItem,
        refreshTrigger,
    ) { debugItem: DebugCardVH.Item?,
        upgradeInfo: UpgradeRepo.Info?,
        setupState: SetupManager.SetupState,
        dataAreaInfo: DataAreaCardVH.Item?,
        corpseFinderItem: CorpseFinderCardVH.Item?,
        systemCleanerItem: SystemCleanerCardVH.Item?,
        appCleanerItem: AppCleanerCardVH.Item?,
        _ ->
        val items = mutableListOf<DashboardAdapter.Item>()

        TitleCardVH.Item(
            upgradeInfo = upgradeInfo
        ).run { items.add(this) }

        debugItem?.let { items.add(it) }

        if (!setupState.isComplete && !isSetupDismissed) {
            SetupCardVH.Item(
                setupState = setupState,
                onDismiss = {
                    isSetupDismissed = true
                    refreshTrigger.value = rngString
                },
                onContinue = {
                    DashboardFragmentDirections.actionDashboardFragmentToSetupFragment(
                        showCompleted = false
                    ).navigate()
                }
            ).run { items.add(this) }
        }

        dataAreaInfo?.let { items.add(it) }

        corpseFinderItem?.let { items.add(it) }
        systemCleanerItem?.let { items.add(it) }
        appCleanerItem?.let { items.add(it) }

        upgradeInfo
            ?.takeIf { !it.isPro }
            ?.let {
                UpgradeCardVH.Item(
                    onUpgrade = { DashboardFragmentDirections.actionDashboardFragmentToUpgradeFragment().navigate() }
                )
            }
            ?.run { items.add(this) }

        items
    }
        .throttleLatest(500)
        .let {
            if (Bugs.isTrace) it.setupCommonEventHandlers(TAG) { "listItems" } else it
        }
        .asLiveData2()


    data class BottomBarState(
        val actionState: Action,
        val leftInfo: CaString?,
        val rightInfo: CaString?,
    ) {
        enum class Action {
            SCAN,
            DELETE,
            WORKING,
            WORKING_CANCELABLE
        }
    }

    val bottomBarState = combine(
        taskManager.state,
        corpseFinder.data,
        systemCleaner.data,
        appCleaner.data,
    ) { taskState, corpseFinderData, systemCleanerData, appCleanerData ->

        BottomBarState(
            actionState = BottomBarState.Action.SCAN,
            leftInfo = null,
            rightInfo = null,
        )
    }
        .asLiveData2()

    fun triggerMainAction() {
        log(TAG) { "triggerMainAction()" }
    }

    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}