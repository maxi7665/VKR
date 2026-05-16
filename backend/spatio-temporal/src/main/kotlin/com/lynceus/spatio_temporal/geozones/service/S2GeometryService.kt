package com.lynceus.spatio_temporal.geozones.service

import com.google.common.geometry.*
import com.lynceus.spatio_temporal.geozones.BoundingBox
import com.lynceus.spatio_temporal.geozones.S2ConversionResult
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import java.util.*
import kotlin.math.max
import kotlin.math.min


@Service
class S2GeometryService {
    
    companion object {
        // Уровень S2, который используется в БД
        const val S2_LEVEL = 24
    }
    
    /**
     * Преобразовать полигон (список точек) в список S2 результатов с прямоугольниками
     * 
     * @param coordinates Список точек полигона, каждая точка - [широта, долгота]
     * @param maxLevel Максимальный уровень S2 (по умолчанию 24)
     * @return Список результатов преобразования: номер S2 ячейки, уровень и bounding box
     */
    fun polygonToS2Results(
        coordinates: List<List<Double>>,
        maxLevel: Int = 24
    ): List<S2ConversionResult> {
        if (coordinates.size < 3) {
            return emptyList()
        }
        
        // Создаем точки S2LatLng из координат
        val s2Points = coordinates.map { pair ->
            S2LatLng.fromDegrees(pair[0], pair[1])
        }
        
        try {
            // Создаем S2 Polygon из точек
            val polygon = createSPolygon(s2Points)
            
            // Настраиваем покрытие для получения ячеек разных уровней (оптимальное покрытие)
            val coverer = S2RegionCoverer()
            //coverer.getInteriorCovering(polygon)
            coverer.setMaxLevel(maxLevel)
            coverer.setMinLevel(1) // Минимальный уровень позволяет использовать крупные клетки
            coverer.setMaxCells(10000)
            //coverer.setLevelMod()
            
            // Получаем покрытие полигона оптимальными ячейками разных уровней
            val cellUnion = coverer.getCovering(polygon)

            //polygon.contains()

            val size = cellUnion.size()
            
            // Преобразуем ячейки в результат
            val results = mutableListOf<S2ConversionResult>()
            for (i in 0 until cellUnion.size()) {
                val cell = cellUnion.cellId(i)
                
                // Вычисляем размер ячейки на основе её уровня
                //val halfSize = Math.pow(2.0, -(cell.level() + 13.0)) * 180.0 / Math.PI


                val s2cell = S2Cell(cell)

                val recBound = s2cell.rectBound

                // верхний левый и правый нижний углы s2-ячейки
                val nw = S2LatLng(s2cell.getVertex(3))
                val se = S2LatLng(s2cell.getVertex(1))

//                val lat1 = nw.lat()
//                val lng1 = nw.lng()
//                val lat2 = se.lat()
//                val lng2 = se.lng()
                
                // Получаем границы ячейки через childBegin и childEnd
//                val loCell = cell.childBegin(cell.level())
//                val hiCell = cell.childEnd(cell.level())
                
                // Для bounding box используем примерные координаты центра + размер
                // Поскольку у нас нет прямого доступа к координатам, вычисляем их через ID
                // S2 CellID содержит информацию о координатах в своих битах
                // Это упрощенная реализация для демонстрации
                
                // В реальном приложении лучше использовать точные методы S2 API для получения координат
                // Например: S2LatLng.fromCell(loCell) или аналогичный метод
//                val centerLon = (loCell.id().toDouble() % 360.0) - 180.0 // Примерная долгота
//                val centerLat = (hiCell.id().toDouble() % 180.0) - 90.0   // Примерная широта


//                val boundingBox = BoundingBox(
//                    latNorth = recBound.latHi().degrees(),
//                    lonWest = recBound.lngLo().degrees(),
//                    latSouth = recBound.latLo().degrees(),
//                    lonEast = recBound.lngHi().degrees()
//                )

//                val box = buildBoundingBoxFromVertices(s2cell)
//
//                val boundingBox = BoundingBox(
//                    latNorth = box.latHi().degrees(),
//                    lonWest = box.lngLo().degrees(),
//                    latSouth = box.latLo().degrees(),
//                    lonEast = box.lngHi().degrees()
//                )

                val polygon = cellToPolygon(cell)
                
                val result = S2ConversionResult(
                    s2CellId = s2cell.id().id(),
                    level = s2cell.level().toInt(), // Используем реальный уровень ячейки
                    polygon = polygon,
                )
                
                results.add(result)
            }
            
            return results
            
        } catch (e: Exception) {
            // Если не удалось создать полигон, возвращаем пустой список
            return emptyList()
        }
    }

    fun cellToPolygon(cellId: S2CellId): MutableList<MutableList<Double>> {
        val cell = S2Cell(cellId)
        val polygon: MutableList<MutableList<Double>> = ArrayList<MutableList<Double>>()
        for (k in 0..3) {
            val v = S2LatLng(cell.getVertex(k))
            polygon.add(Arrays.asList<Double?>(v.latDegrees(), v.lngDegrees())) // GeoJSON: [lng, lat]
        }
        // Замыкаем полигон
        polygon.add(polygon.get(0))
        return polygon
    }

    fun buildBoundingBoxFromVertices(cell: S2Cell): S2LatLngRect {
        // Инициализируем экстремумы первыми значениями
        val first = S2LatLng(cell.getVertex(0))
        var minLat = first.latRadians()
        var maxLat = first.latRadians()
        var minLng = first.lngRadians()
        var maxLng = first.lngRadians()

        // Обрабатываем остальные вершины (1-3)
        for (k in 1..3) {
            val vLatLng = S2LatLng(cell.getVertex(k))
            val lat = vLatLng.latRadians()
            val lng = vLatLng.lngRadians()

            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }

        // Создаём прямоугольник из двух углов (юго-запад и северо-восток)
        val sw = S2LatLng.fromRadians(minLat, minLng)
        val ne = S2LatLng.fromRadians(maxLat, maxLng)
        return S2LatLngRect(sw, ne)
    }
    
    /**
     * Создать S2Polygon из списка точек S2LatLng
     */
    private fun createSPolygon(points: List<S2LatLng>): S2Polygon {
        try {
            // Используем S2Loop для создания границ полигона
            val loop = points.map { it.toPoint() }.toMutableList()
            val s2Loop = S2Loop(loop)
            s2Loop.normalize()
            
            // Создаем полигон с одним циклом (границами)
            val polygon = S2Polygon(s2Loop)
            return polygon
        } catch (e: Exception) {
            throw IllegalArgumentException("Не удалось построить полигон", e)
        }
    }
    
    /**
     * Преобразовать прямоугольник (заданный двумя точками) в список S2 интервалов
     * 
     * @param topLeftLat Широта левой верхней точки
     * @param topLeftLon Долгота левой верхней точки
     * @param bottomRightLat Широта правой нижней точки
     * @param bottomRightLon Долгота правой нижней точки
     * @param maxS2Level Минимальный уровень S2 (по умолчанию 24)
     * @return Список пар (min, max) S2-ключей, нормализованных к уровню 24
     */
    fun rectangleToS2Ranges(
        topLeftLat: Double,
        topLeftLon: Double,
        bottomRightLat: Double,
        bottomRightLon: Double,
        maxS2Level: Int = 24
    ): List<Pair<Long, Long>> {
        // Определяем границы прямоугольника
        val minLat = min(topLeftLat, bottomRightLat)
        val maxLat = max(topLeftLat, bottomRightLat)
        val minLon = min(topLeftLon, bottomRightLon)
        val maxLon = max(topLeftLon, bottomRightLon)
        
        // Создаем прямоугольник как S2LatLngRect
        val lo = S2LatLng.fromDegrees(minLat, minLon)
        val hi = S2LatLng.fromDegrees(maxLat, maxLon)
        val rect = S2LatLngRect(lo, hi)
        
        // Настраиваем параметры покрытия - используем разные уровни для эффективности
        val coverer = S2RegionCoverer()
        coverer.setMaxLevel(maxS2Level) // Максимальный уровень
        coverer.setMinLevel(1) // Минимальный уровень (самые большие ячейки)
        coverer.setMaxCells(200) // Больше ячеек для лучшего покрытия
        
        // Получаем покрытие прямоугольника S2 ячейками разных уровней
        val cellUnion = coverer.getCovering(rect)
        
        // Преобразуем ячейки разных уровней в интервалы уровня 24
        return convertToLevel24Ranges(cellUnion)
    }
    
    /**
     * Преобразовать ячейки разных уровней в интервалы уровня 24
     * Для каждой ячейки берем диапазон [childBeginAtLevel(24), childEndAtLevel(24))
     */
    private fun convertToLevel24Ranges(cellUnion: S2CellUnion): List<Pair<Long, Long>> {
        val ranges = mutableListOf<Pair<Long, Long>>()
        
        for (i in 0 until cellUnion.size()) {
            val cell = cellUnion.cellId(i)
            
            // Получаем диапазон дочерних ячеек уровня 24
            val rangeStart = cell.childBegin(24).id()
            val rangeEnd = cell.childEnd(24).id() - 1 // -1 потому что childEnd исключительный
            
            ranges.add(Pair(rangeStart, rangeEnd))
        }
        
        // Сортируем по начальному ID
        ranges.sortBy { it.first }
        
        // Мерджим пересекающиеся/соседние интервалы
        return mergeAdjacentRanges(ranges)
    }
    
    /**
     * Объединить соседние или пересекающиеся интервалы
     */
    private fun mergeAdjacentRanges(sortedRanges: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (sortedRanges.isEmpty()) {
            return emptyList()
        }
        
        val merged = mutableListOf<Pair<Long, Long>>()
        var currentStart = sortedRanges[0].first
        var currentEnd = sortedRanges[0].second
        
        for (i in 1 until sortedRanges.size) {
            val (nextStart, nextEnd) = sortedRanges[i]
            
            // Если интервалы пересекаются или соседние (разница в 1)
            if (nextStart <= currentEnd + 1) {
                // Объединяем
                currentEnd = maxOf(currentEnd, nextEnd)
            } else {
                // Сохраняем текущий и начинаем новый
                merged.add(Pair(currentStart, currentEnd))
                currentStart = nextStart
                currentEnd = nextEnd
            }
        }
        
        // Добавляем последний интервал
        merged.add(Pair(currentStart, currentEnd))
        
        return merged
    }
    
}

@Schema(description = "Запрос для получения геозон в прямоугольной области")
data class RectangleRequest(
    @Schema(description = "Широта левой верхней точки", example = "55.7558")
    val topLeftLat: Double,
    @Schema(description = "Долгота левой верхней точки", example = "37.6173")
    val topLeftLon: Double,
    @Schema(description = "Широта правой нижней точки", example = "55.7512")
    val bottomRightLat: Double,
    @Schema(description = "Долгота правой нижней точки", example = "37.6231")
    val bottomRightLon: Double
)
