import json
import psycopg2
from psycopg2.extras import Json
from s2sphere import CellId, LatLngRect


def load_zones_data(file_path):
    """Загрузка данных геозон из JSON файла"""
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def connect_to_db():
    """Установка соединения с базой данных PostgreSQL"""
    conn = psycopg2.connect(
        host="localhost",
        port=5432,
        user="user",
        password="password",
        database="lynceus"
    )
    return conn


def calculate_centroid(coordinates):
    """
    Вычисляет центр тяжести (centroid) из массива координат [lon, lat]
    Использует среднее арифметическое для небольших областей
    """
    if not coordinates:
        return 0.0, 0.0
    
    # Извлекаем широты и долготы
    lats = [coord[1] for coord in coordinates]
    lons = [coord[0] for coord in coordinates]
    
    # Среднеарифметическое
    avg_lat = sum(lats) / len(lats)
    avg_lon = sum(lons) / len(lons)
    
    return avg_lat, avg_lon


def calculate_s2_key(level, lat, lon):
    """
    Расчёт S2-ключа заданного уровня с использованием официальной библиотеки s2sphere.
    
    S2 использует икосаэдральную проекцию Земли на клеточной сетке.
    Уровень 24 - клетки размером ~6 см (высокая детализация).
    
    Args:
        level: Уровень S2 индексации (0-30)
        lat: Широта в градусах (-90...90)
        lon: Долгота в градусах (-180...180)
    
    Returns:
        uint64: S2 ключ (CellId) в виде целого числа
    """
    from s2sphere import CellId, LatLng
    
    # Создаём точку на сфере из градусов
    point = LatLng.from_degrees(lat, lon)
    
    # Получаем CellId напрямую из координат
    cell_id = CellId.from_lat_lng(point).parent(level)
    
    # Возвращаем как целое число (id метода возвращает uint64)
    return int(cell_id.id())


def update_geo_zones(conn, zones_data):
    """Обновление геозон: расчёт центра и S2-ключа"""
    updated_count = 0
    skipped_count = 0
    
    with conn.cursor() as cur:
        for zone in zones_data:
            coordinates = []
            
            # Получаем координаты из "list" или "circle"
            if "points" in zone and "list" in zone["points"]:
                for point in zone["points"]["list"]:
                    coordinates.append([point["lat"], point["lon"]])
            
            if not coordinates and "points" in zone and "circle" in zone["points"]:
                for point in zone["points"]["circle"]:
                    coordinates.append([point["lat"], point["lon"]])
            
            # Пропускаем зоны без координат
            if not coordinates:
                print(f"Пропуск зоны '{zone.get('name', 'Unknown')}': нет координат")
                skipped_count += 1
                continue
            
            # Вычисляем центр (centroid)
            center_lat, center_lon = calculate_centroid(coordinates)
            
            # Вычисляем S2 ключ уровня 24
            s2_key = calculate_s2_key(18, center_lat, center_lon)
            
            # Обновляем запись в таблице
            update_query = """
                UPDATE public.geozones
                SET 
                    lat = %s,
                    lon = %s,
                    s2_key = %s
                WHERE "name" = %s
            """
            
            cur.execute(update_query, (point["lat"], point["lon"], s2_key, zone.get("name", "")))
            updated_count += 1
    
    conn.commit()
    print(f"Обновлено геозон: {updated_count}")
    if skipped_count > 0:
        print(f"Пропущено зон: {skipped_count}")


def main():
    try:
        # Загрузка данных
        zones_data = load_zones_data("zones.json")
        print(f"Найдено зон: {len(zones_data)}")
        
        # Подключение к базе данных
        conn = connect_to_db()
        print("Подключено к базе данных PostgreSQL")
        
        # Обновление данных
        update_geo_zones(conn, zones_data)
        
        # Закрытие соединения
        conn.close()
        print("Соединение закрыто")
        
    except Exception as e:
        print(f"Ошибка при обновлении: {e}")
        raise


if __name__ == "__main__":
    main()
