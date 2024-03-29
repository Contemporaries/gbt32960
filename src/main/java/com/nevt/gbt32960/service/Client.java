package com.nevt.gbt32960.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.nevt.gbt32960.codec.GBT32960Decoder;
import com.nevt.gbt32960.codec.GBT32960Encoder;
import com.nevt.gbt32960.paltform.LoginPlatform;
import com.nevt.gbt32960.paltform.PlatformMessage;
import com.nevt.gbt32960.type.EncryptionType;
import com.nevt.gbt32960.type.RequestType;
import com.nevt.gbt32960.type.ResponseTag;
import com.nevt.gbt32960.util.ResponseMessage;
import com.nevt.gbt32960.protocol.ProtocolHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Client {

    private static final String host = "127.0.0.1";
    private static final int port = 32960;

    private static final EventLoopGroup group = new NioEventLoopGroup();
    private static boolean initFlag;

    private static Channel channel;


    public static void main(String[] args) {
        doConnect(new Bootstrap(), group);
    }

    public static void doConnect(Bootstrap bootstrap, EventLoopGroup eventLoopGroup) {
        ChannelFuture f = null;
        try {
            if (bootstrap != null) {
                bootstrap.group(eventLoopGroup);
                bootstrap.channel(NioSocketChannel.class);
                bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
                bootstrap.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
                bootstrap.remoteAddress(host, port);

                // 设置通道初始化
                bootstrap.handler(
                        new ChannelInitializer<SocketChannel>() {
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new GBT32960Decoder());
                                ch.pipeline().addLast(new GBT32960Encoder());
                                ch.pipeline().addLast(ProtocolHandler.getInstance());

                            }
                        }
                );

                f = bootstrap.connect().addListener((ChannelFuture futureListener) ->
                {
                    final EventLoop eventLoop = futureListener.channel().eventLoop();
                    if (!futureListener.isSuccess()) {
                        log.info("与服务端断开连接!在10s之后准备尝试重连!");
                        eventLoop.schedule(() -> doConnect(new Bootstrap(), eventLoop), 10, TimeUnit.SECONDS);

                        initFlag = false;
                    } else {
                        initFlag = true;
                    }
                    if (initFlag) {
                        log.info("连接成功!");
                        channel = futureListener.channel();
                        startConsoleThread();
                    }

                });

                f.channel().closeFuture().sync();
            }
        } catch (Exception e) {
            log.trace("客户端连接失败!" + e.getMessage());
        }

    }

    private static void startConsoleThread() {
        new Thread(() ->
        {
            for (int i = 0; i < 200; i++) {
                try {
                    sendChatMsg();
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static void sendChatMsg() throws InvalidProtocolBufferException {
        log.info("发送消息");
        String VIN = "100000VIN00000000";
        ResponseMessage responseMessage = new ResponseMessage();
        PlatformMessage message = new PlatformMessage();
        message.setRequestType(RequestType.PLATFORM_LOGOUT);
        message.setResponseTag(ResponseTag.COMMAND);
        LoginPlatform data = LoginPlatform.newBuilder()
                .setLoginDaySeq(200)
                .setUsername("FotonToNEVT5")
                .setPassword("FotonToTsinghuaNEVT5")
                .setEncryption(EncryptionType.PLAIN.getValue())
                .build();
        message.setData(data);
        responseMessage.setVin(VIN);
        responseMessage.setMessage(message);
        channel.writeAndFlush(responseMessage);
    }
}
