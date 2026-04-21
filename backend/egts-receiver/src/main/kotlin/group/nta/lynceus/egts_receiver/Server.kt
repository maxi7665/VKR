package group.nta.lynceus.egts_receiver

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.MessageToByteEncoder
import org.springframework.stereotype.Component

@Component
class EgtsServer(private val port: Int) {
    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()

    fun start() {
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline()
                            .addLast(EgtsFrameDecoder())
                            .addLast(EgtsFrameEncoder())
                            .addLast(EgtsServerHandler())
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)

            val f = b.bind(port).sync()
            println("EGTS server started on port $port")
            f.channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }
}

class EgtsFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        val result = buf.readEgtsPacket()
        if (result != null) {
            val (header, frame) = result
            if (header != null) {
                out.add(Pair(header, frame))
            }
        }
    }
}

class EgtsFrameEncoder : MessageToByteEncoder<Pair<Int, ByteArray>>() {
    override fun encode(ctx: ChannelHandlerContext, msg: Pair<Int, ByteArray>, out: ByteBuf) {
        out.writeBytes(msg.second)
    }
}

class EgtsServerHandler : SimpleChannelInboundHandler<Pair<EgtsHeader, ServiceFrame?>>() {
    private val serviceHandlers = mapOf<Byte, ServiceHandler>(
        EGTS_AUTH_SERVICE to AuthServiceHandler(),
        EGTS_TELEDATA_SERVICE to TeleDataServiceHandler(),
        EGTS_COMMANDS_SERVICE to CommandsServiceHandler()
    )

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Pair<EgtsHeader, ServiceFrame?>) {
        val (header, frame) = msg
        when (header.packetType) {
            EGTS_PT_APPDATA -> {
                val responseRecords = mutableListOf<ServiceDataRecord>()
                frame?.records?.forEach { record ->
                    val handler = serviceHandlers[record.recipientServiceType]
                        ?: serviceHandlers[record.sourceServiceType]
                    if (handler != null) {
                        val subresponses = handler.handle(record)
                        if (subresponses.isNotEmpty()) {
                            // Создаём ответную запись с теми же параметрами, но SST/RST могут меняться
                            val respRecord = ServiceDataRecord(
                                recordLength = 0, // будет пересчитано при сборке
                                recordNumber = record.recordNumber,
                                recordFlags = 0, // без OID/EVID/TM
                                objectId = null,
                                eventId = null,
                                time = null,
                                sourceServiceType = record.recipientServiceType,
                                recipientServiceType = record.sourceServiceType,
                                subrecords = subresponses
                            )
                            responseRecords.add(respRecord)
                        }
                    } else {
                        println("No handler for service type ${record.recipientServiceType}")
                        // Отправляем ошибку обработки записи
                        val errSub = createRecordResponse(record.recordNumber, EGTS_PC_ERROR)
                        responseRecords.add(ServiceDataRecord(
                            recordLength = 0, recordNumber = record.recordNumber,
                            recordFlags = 0, objectId = null, eventId = null, time = null,
                            sourceServiceType = record.recipientServiceType,
                            recipientServiceType = record.sourceServiceType,
                            subrecords = listOf(errSub)
                        ))
                    }
                }

                val responseBytes = buildEgtsResponse(header.packetId, EGTS_PC_OK, responseRecords)
                ctx.writeAndFlush(Pair(header.packetId, responseBytes))
            }
            EGTS_PT_RESPONSE -> {
                // Обработка ответа (подтверждения) – в заглушке ничего не делаем
                println("Received response for packet ${header.packetId}")
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }
}

fun main() {
    EgtsServer(7777).start()
}