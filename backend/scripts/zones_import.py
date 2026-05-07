import json
from datetime import datetime
import psycopg2
from psycopg2.extras import Json, execute_values
from psycopg2.extras import register_json


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
    register_json(conn)
    return conn

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


def import_zones(conn, zones_data):
    """Импорт геозон в базу данных"""
    with conn.cursor() as cur:
        for zone in zones_data:
            coordinates = []
            
            # Сначала пробуем взять точки из "list"
            if "points" in zone and "list" in zone["points"]:
                for point in zone["points"]["list"]:
                    coordinates.append([point["lat"], point["lon"]])
            
            # Если точек нет в "list", пробуем "circle"
            if not coordinates and "points" in zone and "circle" in zone["points"]:
                for point in zone["points"]["circle"]:
                    coordinates.append([point["lat"], point["lon"]])
            
            # Для всех зон устанавливаем тип polygon (так как они задаются набором точек)
            zone_type = "polygon"

            s2_key = calculate_s2_key(18, zone['lat'], zone['lon'])
            
            insert_query = """
                INSERT INTO public.geozones ("name", type, coordinates, is_active, lat, lon, s2_key)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            
            values = (
                zone.get("name", ""),
                zone_type,
                Json(coordinates),
                bool(zone.get("active", 1)),
                zone['lat'], zone['lon'],
                s2_key
                )
            
            
            cur.execute(insert_query, values)
    
    conn.commit()
    print(f"Успешно импортировано {len(zones_data)} геозон")


def main():
    try:
        # Загрузка данных
        zones_data = load_zones_data("zones.json")
        
        # Подключение к базе данных
        conn = connect_to_db()
        print("Подключено к базе данных PostgreSQL")
        
        # Импорт данных
        import_zones(conn, zones_data)
        
        # Закрытие соединения
        conn.close()
        print("Соединение закрыто")
        
    except Exception as e:
        print(f"Ошибка при импорте: {e}")
        raise


if __name__ == "__main__":
    main()
