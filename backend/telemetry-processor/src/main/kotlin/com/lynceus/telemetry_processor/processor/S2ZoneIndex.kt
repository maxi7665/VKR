package com.lynceus.telemetry_processor.processor

import com.google.common.geometry.S2Cell
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2CellUnion
import com.google.common.geometry.S2Point
import com.google.common.geometry.S2Region
import com.google.common.geometry.S2RegionCoverer
import com.google.common.geometry.primitives.IntVector

/**
 * Индекс для быстрого поиска S2‑регионов, содержащих данную ячейку targetLevel.
 *
 * @param regions список регионов для индексации.
 * @param coverer экземпляр S2RegionCoverer, используемый для получения покрытий.
 *                Важно: внутри класса для всех покрытий принудительно устанавливается maxLevel = targetLevel.
 * @param targetLevel уровень, до которого нормализуются все покрытия (самый мелкий уровень).
 */
class S2ZoneIndex(
    val regions: List<S2Region>,
    coverer: S2RegionCoverer,
    private val targetLevel: Int
) {
    // Непересекающийся интервал на кривой Гильберта
    private data class Interval(
        val start: Long,      // inclusive
        val end: Long,        // exclusive
        val regionIndices: IntVector)

    // Отсортированный список непересекающихся интервалов
    private val intervals: List<Interval>

    init {
        // Создаём локальный копирующий экземпляр coverer с гарантированным maxLevel
        val localCoverer = S2RegionCoverer.builder()
            .setMaxCells(coverer.maxCells())
            .setMinLevel(coverer.minLevel())
            .setMaxLevel(targetLevel)          // <-- ключевой момент
            .setLevelMod(coverer.levelMod())
            .build()

        // Сырые интервалы (начало, конец, индекс региона)
        data class RawInterval(val start: Long, val end: Long, val regionIndex: Int)

        val rawIntervals = mutableListOf<RawInterval>()

        val prevInterval = LongArray(2)

        // Шаг 1: для каждого региона получаем покрытие и "нарезаем" все ячейки до targetLevel
        for ((index, region) in regions.withIndex()) {
            val covering: S2CellUnion = localCoverer.getCovering(region)

            val list = mutableListOf<Pair<Long, Long>>()

            for (cellId in covering.cellIds()) {
                // localCoverer.maxLevel = targetLevel, поэтому cellId.level() <= targetLevel
                val start = cellId.childBegin(targetLevel).id()
                val end   = cellId.childEnd(targetLevel).id()   // exclusive
                list.add(Pair(start, end))
            }

            // выполняем слияние интервалов для сокращения их количества
            val mergedList = mutableListOf<Pair<Long, Long>>()

            var pos = 0

            for ((start, end) in list) {
                if (pos > 0) {
                    // расширяем интервал
                    if (prevInterval[1] >= start) {
                        prevInterval[1] = end
                    }
                    // не расширить - добавляем запомненный интервал
                    else {
                        mergedList += Pair(prevInterval[0], prevInterval[1])
                        prevInterval[0] = start
                        prevInterval[1] = end
                    }
                }
                else {
                    prevInterval[0] = start
                    prevInterval[1] = end
                }
                pos ++
            }

            // последний интервал
            if (pos > 0) {
                mergedList += Pair(prevInterval[0], prevInterval[1])
            }

            for ((start, end) in mergedList) {
                rawIntervals.add(RawInterval(start, end, index))
            }
        }

        // Шаг 2: заметание (sweep line) для получения непересекающихся интервалов
        // События: точка, тип (+1 – начало, -1 – конец), индекс региона
        val events = mutableListOf<Triple<Long, Int, Int>>()
        for ((start, end, idx) in rawIntervals) {
            events.add(Triple(start,  1, idx))
            events.add(Triple(end,   -1, idx))
        }

        // Сортируем: по точке, при равенстве сначала концы (-1), потом начала (+1)
        events.sortWith(compareBy({ it.first }, { it.second }))

        val result = mutableListOf<Interval>()
        val activeRegions = mutableSetOf<Int>()
        var lastPoint: Long? = null
        var i = 0

        while (i < events.size) {
            val currentPoint = events[i].first

            // Если между lastPoint и currentPoint было активное множество, фиксируем интервал
            if (lastPoint != null && lastPoint < currentPoint && activeRegions.isNotEmpty()) {
                result.add(Interval(
                    lastPoint,
                    currentPoint,
                    IntVector.copyOf(
                        activeRegions.sorted())))
            }


            // Обрабатываем все события в точке currentPoint
            while (i < events.size && events[i].first == currentPoint) {
                val (_, type, idx) = events[i]
                if (type == 1) activeRegions.add(idx)
                else activeRegions.remove(idx)
                i++
            }
            lastPoint = currentPoint
        }

        intervals = result   // уже отсортированы по start
    }


    /**
     * Возвращает все регионы, в которые попадает S2‑ячейка на уровне targetLevel.
     * Если переданная ячейка имеет другой уровень, она сначала приводится к targetLevel
     * (выбирается родитель нужного уровня).
     */
    fun findRegions(p: S2Point): IntVector {
//        val id = if (cellId.level() == targetLevel) {
//            cellId.id()
//        } else {
//            cellId.parent(targetLevel).id()
//        }

        val id = S2CellId.fromPoint(p).parent(targetLevel).id()

        val idx = intervals.binarySearch { interval ->
            when {
                id < interval.start -> 1
                id >= interval.end -> -1
                else -> 0
            }
        }



        val ret = if (idx >= 0) {
            val indexes = intervals[idx].regionIndices
            var isViolation = false
            for (i in 0..<indexes.size()) {
                if (!regions[indexes[i]].contains(p)) {
                    isViolation = true
                    break
                }
            }

            // если нет нарушений - возвращаем сразу, иначе - плохой случай,
            // придется аллоцировать новый и проверять на вхождения
            if (!isViolation) {
                indexes
            }
            else {
                val vec = IntVector()
                for (i in 0..<indexes.size()) {
                    if (regions[indexes[i]].contains(p)) {
                        vec.add(indexes[i])
                    }
                }
                vec
            }
        }
        else IntVector.empty()

        return ret

//        return if (ret.isNotEmpty()) {
//            val cell = S2Cell(cellId)
//            ret.filter { it.contains(cell) }
//        } else {
//            ret
//        }
    }
}