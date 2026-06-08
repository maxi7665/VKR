package com.lynceus.telemetry_processor.processor

import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2Point
import com.google.common.geometry.S2Region
import com.google.common.geometry.S2RegionCoverer
import com.google.common.geometry.primitives.IntVector

class S2ZoneIndex(
    val regions: List<S2Region>,
    coverer: S2RegionCoverer,
    private val targetLevel: Int
) {
    private val intervalStarts: LongArray
    private val intervalEnds: LongArray
    private val regionIndicesPerInterval: Array<IntArray>

    // Вспомогательный класс для событий заметания
    private class SweepEvent(
        val point: Long,
        val regionIdx: Int,
        val isStart: Boolean
    )

    init {
        val localCoverer = S2RegionCoverer.builder()
            .setMaxCells(coverer.maxCells())
            .setMinLevel(coverer.minLevel())
            .setMaxLevel(targetLevel)
            .setLevelMod(coverer.levelMod())
            .build()

        // Сырые сегменты одного региона (для локального сжатия)
        class RawSegment(val start: Long, var end: Long, val regionIdx: Int)
        val initialSegments = mutableListOf<RawSegment>()

        // ШАГ 1: Получение покрытий и локальное слияние смежных ячеек для каждого региона отдельно
        for ((idx, region) in regions.withIndex()) {
            val covering = localCoverer.getCovering(region)
            var prevSeg: RawSegment? = null

            for (cellId in covering.cellIds()) {
                val start = cellId.childBegin(targetLevel).id()
                val end = cellId.childEnd(targetLevel).id()

                if (prevSeg != null && prevSeg.end == start) {
                    prevSeg.end = end // Сливаем смежные участки
                } else {
                    val newSeg = RawSegment(start, end, idx)
                    initialSegments.add(newSeg)
                    prevSeg = newSeg
                }
            }
        }

        // ШАГ 2: Создание списка событий для Sweep-line
        val events = ArrayList<SweepEvent>(initialSegments.size * 2)
        for (seg in initialSegments) {
            events.add(SweepEvent(seg.start, seg.regionIdx, true))
            events.add(SweepEvent(seg.end, seg.regionIdx, false))
        }
        // Сортировка: O(M log M)
        events.sortWith(
            compareBy<SweepEvent> { it.point }
                .thenByDescending { it.isStart }
        )

        val groupedEvents = events.groupBy { it.point }

        // Списки для сборки финальных интервалов
        val startsList = mutableListOf<Long>()
        val endsList = mutableListOf<Long>()
        val regionsList = mutableListOf<IntArray>()

        // Эффективный трекинг активных регионов через BitSet
        val activeRegions = hashSetOf<Int>()
        var lastPoint = -1L
        //var activeRegionsNum = 0

        // идем по точкам пространства
        for ((point, events) in groupedEvents) {

            // добавляем участок, если на нем есть интервалы
            if (lastPoint != -1L &&
                activeRegions.isNotEmpty()) {
                startsList.add(lastPoint)
                endsList.add(point)
                regionsList.add(activeRegions.sorted().toIntArray())
            }

            // помечаем новые активные зоны
            for (event in events) {
                if (event.isStart) {
                    activeRegions += event.regionIdx
                }
                else {
                    activeRegions -= event.regionIdx
                }
            }

            lastPoint = point
        }

        // Переносим результат в финальные плоские массивы
        intervalStarts = startsList.toLongArray()
        intervalEnds = endsList.toLongArray()
        regionIndicesPerInterval = regionsList.toTypedArray()
    }

    // вектор для хранения результатов при поиске регионов,
    // чтобы избежать аллокации массива при каждом вызове метода
    private val resultVector = IntVector()

    fun findRegions(p: S2Point): IntArray {
        val id = S2CellId.fromPoint(p)
            .parent(targetLevel).id()

        // инициализация окна бинарного поиска
        var low = 0
        var high = intervalStarts.size - 1
        var idx = -1

        // бинарный поиск
        while (low <= high) {
            // серединное значение окна поиска = (start + end) / 2
            val mid = (low + high) ushr 1

            // достаем начало и конец
            val start = intervalStarts[mid]
            val end = intervalEnds[mid]

            when {
                id < start -> high = mid - 1 // id до середины - берем меньшую половину
                id >= end -> low = mid + 1 // id после середины - берем большую половину
                else -> {
                    idx = mid // нашли результат
                    break
                }
            }
        }

        if (idx == -1) return EMPTY_INT_ARRAY

        val candidates = regionIndicesPerInterval[idx]

        resultVector.clear()

        for (i in candidates.indices) {
            val rIdx = candidates[i]
            if (regions[rIdx].contains(p)) {
                resultVector.add(rIdx)
            }
        }

        return if (resultVector.size() == candidates.size)
            candidates
        else
            resultVector.toArray()
    }

    companion object {
        private val EMPTY_INT_ARRAY = IntArray(0)
    }
}
