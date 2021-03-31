package com.nevt.gbt32960.codec;

import com.nevt.gbt32960.message.*;
import com.nevt.gbt32960.paltform.LoginPlatform;
import com.nevt.gbt32960.paltform.PlatformMessage;
import com.nevt.gbt32960.type.EncryptionType;
import com.nevt.gbt32960.type.FrameHeader;
import com.nevt.gbt32960.type.RequestType;
import com.nevt.gbt32960.type.ResponseTag;
import com.nevt.gbt32960.util.GBT32960Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static com.nevt.gbt32960.type.FrameHeader.HEADER_LENGTH;
import static com.nevt.gbt32960.util.GBT32960Message.*;

@Slf4j
public class GBT32960Decoder extends ReplayingDecoder<Void> {

    private FrameHeader frameHeader;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int startIndex = in.readerIndex();
        checkpoint();
//        log.info("帧消息: {}", ByteBufUtil.hexDump(internalBuffer()));

        if (in.readShort() != START_SYMBOL) {
            System.out.println("NO START_SYMBOL");
            in.skipBytes(actualReadableBytes());
            ctx.close();
            return;
        }

        frameHeader = decodeFrameHeader(in);

        int payloadLength = frameHeader.getPayloadLength();

        byte nowCheckCode = checkCode(in, startIndex, HEADER_LENGTH + 2 + payloadLength);

        byte sourceCheckCode = in.getByte(HEADER_LENGTH + 2 + payloadLength + startIndex);

        if (nowCheckCode != sourceCheckCode) {
            log.info("消息校验位验证失败: {} vs {}", String.format("%02X", nowCheckCode),
                    String.format("%02X", sourceCheckCode));
            return;
        }

        Object dataUnit = decodeDataUnit(in, frameHeader);

        GBT32960Message message = GBT32960Message.builder()
                .header(frameHeader)
                .dataUnit(dataUnit)
                .build();
        log.info("Analysis complete And Release");

        out.add(message);

        in.skipBytes(1);

    }

    private Object decodeDataUnit(ByteBuf in, FrameHeader header) {
        switch (header.getRequestType()) {
            case REAL_TIME:
            case REISSUE:
                return realTime(in, header);
            case PLATFORM_LOGIN:
                return decodeLoginPlatform(in);
            case PLATFORM_LOGOUT:
                return decodeLogoutPlatform(in);
            case PLATFORM_LOGOUT_RESPONSE:
            case PLATFORM_LOGIN_RESPONSE:
                return header.getResponseTag();
            case LOGIN:
                LoginRequest loginRequest = decodeLogin(in);
                log.info("登入信息: \n数据采集时间:{}\n{}", ZonedDateTime.ofInstant(Instant.ofEpochSecond(loginRequest.getRecordTime()), ZONE_UTC8), loginRequest);
                return loginRequest;
            case LOGOUT:
                LogoutRequest logout = LogoutRequest.newBuilder()
                        .setRecordTime(readTime(in))
                        .setLogoutDaySeq(in.readUnsignedShort()).build();
                log.info("登出消息: \n数据采集时间:{}\n{}", ZonedDateTime.ofInstant(Instant.ofEpochSecond(logout.getRecordTime()), ZONE_UTC8), logout);
                return logout;
            default:
                throw new Error();
        }
    }

    /**
     * 解析 登入数据
     *
     * @param in ByteBuf
     * @return 登入数据
     */
    private LoginRequest decodeLogin(ByteBuf in) {
        LoginRequest.Builder builder = LoginRequest.newBuilder()
                .setRecordTime(readTime(in))
                .setLoginDaySeq(in.readUnsignedShort())
                .setIccid(in.readCharSequence(20, ASCII_CHARSET).toString());
        int count = in.readByte();
        int length = in.readByte();
        builder.setSystemCodeLength(length);
        for (int i = 0; i < count; i++) {
            builder.addChargeableSubsystemCode(
                    in.readCharSequence(length, ASCII_CHARSET).toString());
        }
        return builder.build();
    }


    /**
     * @param in ByteBuf
     * @return LoginPlatform
     * @author lihuang
     */
    private LoginPlatform decodeLoginPlatform(ByteBuf in) {
        return LoginPlatform.decoderFul(in);
    }

    private LoginPlatform decodeLogoutPlatform(ByteBuf in) {
        return LoginPlatform.decoderLogout(in);
    }

    /**
     * 解析头部数据
     *
     * @param in ByteBuf
     * @return 头部数据
     */
    private FrameHeader decodeFrameHeader(ByteBuf in) {
        frameHeader = FrameHeader.builder()
                .requestType(RequestType.valueOf(in.readByte()))
                .responseTag(ResponseTag.valueOf(in.readByte()))
                .vin(in.readCharSequence(17, GBT32960Message.ASCII_CHARSET).toString())
                .encryptionType(EncryptionType.valueOf(in.readByte()))
                .payloadLength(in.readUnsignedShort())
                .build();
        return frameHeader;
    }

    /**
     * 计算校验码
     *
     * @param in     ByteBuf
     * @param start  开始位置
     * @param length 长度
     * @return 校验码
     */
    private byte checkCode(ByteBuf in, int start, int length) {
        if (length == 0) {
            return 0;
        }
        FrameHeader.CheckCodeProcessor processor = new FrameHeader.CheckCodeProcessor();
        in.forEachByte(start, length, processor);
        return processor.getCheckCode();
    }

    private RealTimeReport realTime(ByteBuf in, FrameHeader header) {
        ByteBuf fullBody = in.readRetainedSlice(header.getPayloadLength());
        RealTimeReport.Builder report = ReportDecoder.decodeFully(fullBody);
        fullBody.release();
        report.setReissue(header.getRequestType() == RequestType.REISSUE);
        return report.build();
    }

}
