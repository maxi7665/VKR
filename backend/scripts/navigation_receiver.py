import asyncio
import websockets
from confluent_kafka import Producer
from confluent_kafka.admin import AdminClient, NewTopic

# Настройки
WS_URL = "ws://localhost:8765"  # Замените на ваш адрес
KAFKA_BROKER = "localhost:9092"
TOPIC_NAME = "lynceus.prod.navigation"

def setup_kafka_topic(topic_name):
    """Создает топик в Kafka, если он еще не существует"""
    admin_client = AdminClient({"bootstrap.servers": KAFKA_BROKER})
    
    new_topic = NewTopic(
        topic_name, 
        num_partitions=1, 
        replication_factor=1, 
        config= {"retention.ms": "3600000"})
    
    
    # Создаем топик (метод асинхронный в API, но здесь мы ждем результат)
    fs = admin_client.create_topics([new_topic])
    
    for topic, f in fs.items():
        try:
            f.result()  # Дождется завершения операции
            print(f"Топик '{topic}' успешно создан.")
        except Exception as e:
            print(f"Топик '{topic}' уже существует или произошла ошибка: {e}")

async def listen_and_forward():
    # Настройка продюсера
    conf = {"bootstrap.servers": KAFKA_BROKER}
    producer = Producer(conf)

    def delivery_report(err, msg):
        if err is not None:
            print(f"Ошибка доставки: {err}")
        else:
            pass
            # print(f"Сообщение доставлено в {msg.topic()} [{msg.partition()}]")

    print(f"Подключение к WebSocket: {WS_URL}...")
    
    try:
        async with websockets.connect(WS_URL) as websocket:
            print("Соединение установлено. Слушаю сообщения...")
            async for message in websocket:
                # Отправка в Kafka
                producer.produce(
                    TOPIC_NAME, 
                    value=message.encode('utf-8'), 
                    callback=delivery_report
                )
                # Опрашиваем события для работы callback-ов
                producer.poll(0)
                # print(f"Получено и отправлено в буфер: {message}")
                
    except Exception as e:
        print(f"Ошибка соединения: {e}")
    finally:
        producer.flush() # Ждем отправки всех сообщений перед выходом

if __name__ == "__main__":
    # 1. Декларируем топик
    setup_kafka_topic(TOPIC_NAME)
    
    # 2. Запускаем цикл прослушивания
    try:
        asyncio.run(listen_and_forward())
    except KeyboardInterrupt:
        print("\nОстановка скрипта...")
