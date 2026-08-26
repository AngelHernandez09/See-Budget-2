package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.ProjectionSnapshot
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import kotlinx.datetime.LocalDate

/**
 * RF-09 — "Historial de proyecciones" (documento, pantalla 8): "Detalle
 * de qué cambió respecto al snapshot anterior, al tocar uno". Comparación
 * pura entre dos `ProjectionSnapshot` — vive en `commonMain` junto al
 * resto de `domain/projection`, sin depender de la UI, para poder
 * testearla igual que `ProjectionEngine`.
 *
 * "Anterior" se define por orden cronológico de `createdAt` — quien
 * llama a [diff] es responsable de pasar los dos snapshots en ese orden
 * ([previous] más viejo, [current] más nuevo). Los ítems se emparejan
 * por `plannedItemId`; uno que aparece en [current] pero no en
 * [previous] es "agregado", al revés es "eliminado", y si aparece en
 * ambos pero con algún campo distinto es "modificado" (comparación por
 * `equals` de la data class completa).
 */
object ProjectionSnapshotComparer {
    fun diff(previous: ProjectionSnapshot, current: ProjectionSnapshot): ProjectionSnapshotDiff {
        val initialBalanceChange = changeOrNull(previous.frozenInitialBalance, current.frozenInitialBalance)
        val startDateChange = changeOrNull(previous.frozenStartDate, current.frozenStartDate)
        val baseCurrencyChange = changeOrNull(previous.frozenBaseCurrency, current.frozenBaseCurrency)

        val previousById = previous.frozenPlannedItems.associateBy { it.plannedItemId }
        val currentById = current.frozenPlannedItems.associateBy { it.plannedItemId }

        val added = current.frozenPlannedItems.filter { it.plannedItemId !in previousById }
        val removed = previous.frozenPlannedItems.filter { it.plannedItemId !in currentById }
        val changed = current.frozenPlannedItems.mapNotNull { currentItem ->
            val previousItem = previousById[currentItem.plannedItemId] ?: return@mapNotNull null
            if (previousItem != currentItem) ItemChange(previousItem, currentItem) else null
        }

        return ProjectionSnapshotDiff(
            initialBalanceChange = initialBalanceChange,
            startDateChange = startDateChange,
            baseCurrencyChange = baseCurrencyChange,
            addedItems = added,
            removedItems = removed,
            changedItems = changed,
        )
    }

    private fun <T> changeOrNull(previous: T, current: T): Change<T>? =
        if (previous != current) Change(previous, current) else null
}

data class Change<T>(val previous: T, val current: T)

data class ItemChange(val previous: ProjectionSnapshotItem, val current: ProjectionSnapshotItem)

data class ProjectionSnapshotDiff(
    val initialBalanceChange: Change<Long>?,
    val startDateChange: Change<LocalDate>?,
    val baseCurrencyChange: Change<String>?,
    val addedItems: List<ProjectionSnapshotItem>,
    val removedItems: List<ProjectionSnapshotItem>,
    val changedItems: List<ItemChange>,
) {
    val hasChanges: Boolean
        get() = initialBalanceChange != null || startDateChange != null || baseCurrencyChange != null ||
            addedItems.isNotEmpty() || removedItems.isNotEmpty() || changedItems.isNotEmpty()
}
