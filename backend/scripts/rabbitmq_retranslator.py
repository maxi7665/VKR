
from datetime import time
import json
import logging
from time import sleep

import pika
# import socks
# import socket

# Настройка прокси
# socks.set_default_proxy(socks.SOCKS5, "localhost", 1080)
# socket.socket = socks.socksocket

# ---------- Параметры из YAML ----------
RABBITMQ_HOST = "localhost"
RABBITMQ_PORT = 5672
USER = "disp"
PASS = "qwsxza"
VHOST = "/"

EXCHANGE = "disp.points"
QUEUE = "prod.disp.points.lynceus"
ROUTING_KEY = "#"

PREFETCH = 500
BATCH_SIZE = 100
RECEIVED_TIMEOUT_MS = 30000        # consumer timeout (x-consumer-timeout)
CONN_TIMEOUT_MS = 10000            # socket connect timeout

RECOVERY_INIT_MS = 5000
RECOVERY_MAX_MS = 60000
RECOVERY_MULT = 2.0
FAILED_DECL_RETRY_MS = 10000

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def get_connection():
    """Создаёт BlockingConnection с параметрами из конфига."""
    creds = pika.PlainCredentials(USER, PASS)
    params = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=RABBITMQ_PORT,
        virtual_host=VHOST,
        credentials=creds,
        socket_timeout=CONN_TIMEOUT_MS / 1000,
        heartbeat=30,  # помогает быстрее ловить обрыв,
        client_properties={
            'connection_name': 'lynceus_client'
        }
    )
    return pika.BlockingConnection(params)


def declare_topology(channel):
    """Объявляет exchange, queue и связку. При ошибке — пауза и повтор."""
    while True:
        try:
            channel.exchange_declare(exchange=EXCHANGE,
                                     exchange_type='fanout',
                                     durable=True)
            channel.queue_declare(queue=QUEUE, durable=False, auto_delete=True)
            channel.queue_bind(exchange=EXCHANGE,
                               queue=QUEUE,
                               routing_key=ROUTING_KEY)
            logger.info("Топология объявлена")
            break
        except Exception as e:
            logger.error("Ошибка декларации: %s. Повтор через %d мс",
                         e, FAILED_DECL_RETRY_MS)
            time.sleep(FAILED_DECL_RETRY_MS / 1000)

cnt = 0

def run():
    """Главный цикл consumer'а с переподключениями."""
    backoff = RECOVERY_INIT_MS / 1000.0  # начальная задержка (сек)

    while True:
        connection = None
        try:
            # 1. Подключаемся
            connection = get_connection()
            channel = connection.channel()

            # 2. Настраиваем топологию
            declare_topology(channel)

            # 3. Prefetch
            channel.basic_qos(prefetch_count=PREFETCH)

            # 4. Consumer timeout (RabbitMQ >= 3.12)
            consume_args = {}
            if RECEIVED_TIMEOUT_MS > 0:
                consume_args['x-consumer-timeout'] = RECEIVED_TIMEOUT_MS

            

            def on_message(ch, method, properties, body):
                # logger.info("Получено сообщение: %s", body)
                # ch.basic_ack(delivery_tag=method.delivery_tag)
                # global cnt
                # cnt+=1
                # if (cnt % 1000 == 0):
                #     logger.info("Получено %d сообщений", cnt)
                pass
            
            consumer_tag = channel.basic_consume(
                queue=QUEUE,
                auto_ack=True,
                arguments=consume_args if consume_args else None,
                on_message_callback=on_message
            )

            channel.start_consuming()

            return

            # logger.info(
            #     f"Подключён consumer {consumer_tag}. Ожидание сообщений (batch=%d, prefetch=%d)",
            #     BATCH_SIZE, 
            #     PREFETCH)

            # 6. Цикл пакетной обработки
            batch_tags = []   # накапливаем delivery_tag'и сообщений
            while True:
                # Получаем одно сообщение (неблокирующе с таймаутом 1 сек)
                method, properties, body = channel.basic_get(
                    queue=QUEUE, auto_ack=True
                )

                if method is None:
                    # Нет сообщения — просто продолжаем ждать
                    time.sleep(0.1)
                    continue

                # json_body = json.loads(body)

                # Обрабатываем сообщение (здесь может быть ваша логика)
                # logger.debug("Получено: %s (tag=%d)", json_body, method.delivery_tag)

                batch_tags.append(method.delivery_tag)

                # Если набрали батч — подтверждаем все сразу
                # if len(batch_tags) >= BATCH_SIZE:
                #     # multiple=True – ack все до последнего включительно
                #     channel.basic_ack(
                #         delivery_tag=batch_tags[-1], multiple=True
                #     )
                #     logger.info("Подтверждён батч из %d сообщений", len(batch_tags))
                #     batch_tags.clear()

        except KeyboardInterrupt:
            logger.info("Остановлен пользователем")
            break
        except (pika.exceptions.AMQPConnectionError,
                pika.exceptions.AMQPChannelError) as e:
            logger.error("Соединение потеряно: %s", e)
        except Exception as e:
            logger.exception("Неожиданная ошибка: %s", e)
        finally:
            # Закрываем соединение, если оно живо
            try:
                if connection and connection.is_open:
                    connection.close()
            except Exception:
                pass

        # Экспоненциальный backoff перед переподключением
        logger.info("Повторная попытка через %.1f сек", backoff)
        sleep(backoff)
        backoff = min(backoff * RECOVERY_MULT, RECOVERY_MAX_MS / 1000.0)


if __name__ == "__main__":
    run()


