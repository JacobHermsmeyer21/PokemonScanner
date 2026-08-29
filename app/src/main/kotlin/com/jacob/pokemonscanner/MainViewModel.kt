package com.jacob.pokemonscanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacob.pokemonscanner.automation.AutomaticScanCoordinator
import com.jacob.pokemonscanner.database.PokemonRecordDao
import com.jacob.pokemonscanner.database.PokemonRecordEntity
import com.jacob.pokemonscanner.domain.FingerprintBuilder
import com.jacob.pokemonscanner.image.PokemonScreenAnalyzer
import com.jacob.pokemonscanner.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val records: PokemonRecordDao,
) : ViewModel() {
    private val coordinator = AutomaticScanCoordinator(PokemonScreenAnalyzer())
    val scannerState = coordinator.state

    private val mutableInventoryQuery = MutableStateFlow("")
    private val mutableInventorySort = MutableStateFlow(InventorySort.SCANNED_NEWEST)
    val inventory = combine(records.observeAll(), mutableInventoryQuery, mutableInventorySort) { allRecords, query, sort ->
        val filtered = allRecords.filterByInventoryQuery(query)
        InventoryUiState(
            query = query,
            sort = sort,
            records = filtered.sortedFor(sort),
            totalCount = allRecords.size,
            filteredCount = filtered.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    private val mutableProfile = MutableStateFlow(AutomationProfile())
    val profile = mutableProfile.asStateFlow()

    fun updateAppraisePoint(x: Float = profile.value.appraiseMenuItem.x, y: Float = profile.value.appraiseMenuItem.y) {
        mutableProfile.value = profile.value.copy(appraiseMenuItem = NormalizedPoint(x, y))
    }

    fun updateInventorySearch(query: String) {
        mutableInventoryQuery.value = query
    }

    fun updateInventorySort(sort: InventorySort) {
        mutableInventorySort.value = sort
    }

    fun startAutomatic() {
        coordinator.start(viewModelScope, profile.value) { scan -> records.insert(scan.toEntity()) }
    }

    fun pause() = coordinator.pause()
    fun stop() = coordinator.stop()

    private fun PokemonScan.toEntity(): PokemonRecordEntity {
        val iv = requireNotNull(ivs.value) { "Cannot save without appraisal IVs" }
        return PokemonRecordEntity(
            speciesName = speciesName.value ?: "Unknown",
            rawOcrName = speciesName.rawValue,
            nickname = nickname.value,
            form = form.value,
            cp = cp.value ?: 10,
            level = possibleLevels.singleOrNull(),
            possibleLevels = possibleLevels,
            attackIv = iv.attack,
            defenseIv = iv.defense,
            staminaIv = iv.stamina,
            totalIv = iv.total,
            ivPercentage = iv.percentage,
            currentHp = currentHp.value,
            isShiny = shiny.toBooleanOrNull(),
            isFavorite = favorite.toBooleanOrNull(),
            scannedAt = Instant.now(),
            scanSessionId = null,
            detailScreenshotPath = null,
            appraisalScreenshotPath = null,
            screenshotHash = screenshotHash,
            recordFingerprint = FingerprintBuilder.build(this),
            nameConfidence = speciesName.confidence,
            cpConfidence = cp.confidence,
            ivConfidence = ivs.confidence,
            levelConfidence = levelConfidence,
        )
    }

    private fun TriState.toBooleanOrNull(): Boolean? = when (this) {
        TriState.YES -> true
        TriState.NO -> false
        TriState.UNKNOWN -> null
    }

    private fun List<PokemonRecordEntity>.filterByInventoryQuery(query: String): List<PokemonRecordEntity> {
        val term = query.trim()
        if (term.isEmpty()) return this
        return filter { it.matchesInventoryQuery(term) }
    }

    private fun PokemonRecordEntity.matchesInventoryQuery(term: String): Boolean {
        val ivText = "$attackIv/$defenseIv/$staminaIv"
        val searchable = listOfNotNull(
            speciesName,
            rawOcrName,
            nickname,
            form,
            notes,
            cp.toString(),
            totalIv.toString(),
            "%.1f".format(ivPercentage),
            ivText,
        )
        return searchable.any { it.contains(term, ignoreCase = true) }
    }

    private fun List<PokemonRecordEntity>.sortedFor(sort: InventorySort): List<PokemonRecordEntity> = when (sort) {
        InventorySort.SCANNED_NEWEST -> sortedWith(compareByDescending<PokemonRecordEntity> { it.scannedAt }.thenByDescending { it.id })
        InventorySort.SCANNED_OLDEST -> sortedWith(compareBy<PokemonRecordEntity> { it.scannedAt }.thenBy { it.id })
        InventorySort.NAME_ASC -> sortedWith(compareBy<PokemonRecordEntity> { it.speciesName.lowercase() }.thenByDescending { it.scannedAt })
        InventorySort.NAME_DESC -> sortedWith(compareByDescending<PokemonRecordEntity> { it.speciesName.lowercase() }.thenByDescending { it.scannedAt })
        InventorySort.CP_HIGH -> sortedWith(compareByDescending<PokemonRecordEntity> { it.cp }.thenByDescending { it.ivPercentage })
        InventorySort.CP_LOW -> sortedWith(compareBy<PokemonRecordEntity> { it.cp }.thenByDescending { it.ivPercentage })
        InventorySort.IV_HIGH -> sortedWith(compareByDescending<PokemonRecordEntity> { it.ivPercentage }.thenByDescending { it.cp })
        InventorySort.IV_LOW -> sortedWith(compareBy<PokemonRecordEntity> { it.ivPercentage }.thenByDescending { it.cp })
        InventorySort.ATTACK_HIGH -> sortedWith(compareByDescending<PokemonRecordEntity> { it.attackIv }.thenByDescending { it.ivPercentage })
        InventorySort.DEFENSE_HIGH -> sortedWith(compareByDescending<PokemonRecordEntity> { it.defenseIv }.thenByDescending { it.ivPercentage })
        InventorySort.STAMINA_HIGH -> sortedWith(compareByDescending<PokemonRecordEntity> { it.staminaIv }.thenByDescending { it.ivPercentage })
    }
}

data class InventoryUiState(
    val query: String = "",
    val sort: InventorySort = InventorySort.SCANNED_NEWEST,
    val records: List<PokemonRecordEntity> = emptyList(),
    val totalCount: Int = 0,
    val filteredCount: Int = 0,
)

enum class InventorySort(val label: String) {
    SCANNED_NEWEST("Newest scan"),
    SCANNED_OLDEST("Oldest scan"),
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    CP_HIGH("CP high-low"),
    CP_LOW("CP low-high"),
    IV_HIGH("IV high-low"),
    IV_LOW("IV low-high"),
    ATTACK_HIGH("Attack high"),
    DEFENSE_HIGH("Defense high"),
    STAMINA_HIGH("HP high"),
}
