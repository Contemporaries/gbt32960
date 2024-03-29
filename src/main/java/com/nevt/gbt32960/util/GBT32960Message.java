package com.nevt.gbt32960.util;

import com.nevt.gbt32960.message.EmptyResponse;
import com.nevt.gbt32960.paltform.PlatformMessage;
import com.nevt.gbt32960.type.EncryptionType;
import com.nevt.gbt32960.type.FrameHeader;
import com.nevt.gbt32960.type.RequestType;
import com.nevt.gbt32960.type.ResponseTag;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.Builder;
import lombok.Getter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

@Builder
@Getter
public class GBT32960Message {

    private final FrameHeader header;
    private final Object dataUnit;
    private final boolean valid;

    public static final int MAX_LENGTH = Short.MAX_VALUE * 2 + 25;

    public static final byte EXCEPTION_MARK = (byte) 0xFE;
    public static final byte INVALID_MARK = (byte) 0xFF;

    public static final short START_SYMBOL = 0x2323;
    public static final int START_SYMBOL_LENGTH = 2;

    public static final ZoneId ZONE_UTC8 = ZoneId.of("UTC+8");
    public static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;
    public static final Charset CHINESE_CHARSET = StandardCharsets.UTF_8;

    public static long readTime(ByteBuf in) {
        return ZonedDateTime.of(in.readByte() + 2000,
                in.readByte(),
                in.readByte(),
                in.readByte(),
                in.readByte(),
                in.readByte(),
                0, ZONE_UTC8).toEpochSecond();
    }

    public static void writeTime(ByteBuf out, Long epochSecond) {
        writeTime(out, Instant.ofEpochSecond(epochSecond));
    }

    public static void writeTime(ByteBuf out, Instant instant) {
        ZonedDateTime time = instant.atZone(ZONE_UTC8);
        writeTime(out, time);
    }

    private static void writeTime(ByteBuf out, ZonedDateTime time) {
        out.writeByte(time.getYear() - 2000);
        out.writeByte(time.getMonthValue());
        out.writeByte(time.getDayOfMonth());
        out.writeByte(time.getHour());
        out.writeByte(time.getMinute());
        out.writeByte(time.getSecond());
    }

    public static void encodeCommand(ByteBuf out, String vin, RequestType type, Consumer<ByteBuf> payloadEncoder) {
        encodeMessage(out, vin, type, ResponseTag.COMMAND, payloadEncoder);
    }

    public static void encodeMessage(ByteBuf out, String vin, RequestType type,
                                     ResponseTag tag, Consumer<ByteBuf> payloadEncoder) {
        out.writeShort(START_SYMBOL);
        int startIndex = out.writerIndex();
        out.writeByte(type.getValue());
        out.writeByte(tag.getValue());
        out.writeBytes(vin.getBytes(ASCII_CHARSET));
        out.writeByte(EncryptionType.PLAIN.getValue());
        int lengthIndex = out.writerIndex();
        // 占位
        out.writeShort(0);
        int payloadBegin = out.writerIndex();
        payloadEncoder.accept(out);
        int payloadLength = out.writerIndex() - payloadBegin;
        // 回写长度
        out.setShort(lengthIndex, payloadLength);
        FrameHeader.CheckCodeProcessor checkCodeProcessor = new FrameHeader.CheckCodeProcessor();
        out.forEachByte(startIndex, FrameHeader.HEADER_LENGTH + payloadLength, checkCodeProcessor);
        out.writeByte(checkCodeProcessor.getCheckCode());
    }


    public static void loginResponse(ChannelHandlerContext ctx, ByteBuf out, String vin, ResponseTag responseTag) {
        out.writeShort(START_SYMBOL);
        int startIndex = out.writerIndex();
        out.writeByte(RequestType.PLATFORM_LOGIN_RESPONSE.getValue());
        out.writeByte(responseTag.getValue());
        out.writeBytes(vin.getBytes(ASCII_CHARSET));
        out.writeByte(EncryptionType.PLAIN.getValue());
        int lengthIndex = out.writerIndex();
        out.writeShort(0);
        FrameHeader.CheckCodeProcessor checkCodeProcessor = new FrameHeader.CheckCodeProcessor();
        out.forEachByte(startIndex, FrameHeader.HEADER_LENGTH, checkCodeProcessor);
        out.writeByte(checkCodeProcessor.getCheckCode());
    }

    public static void logoutResponse(ChannelHandlerContext ctx, ByteBuf out, String vin, ResponseTag responseTag) {
        out.writeShort(START_SYMBOL);
        int startIndex = out.writerIndex();
        out.writeByte(RequestType.PLATFORM_LOGOUT_RESPONSE.getValue());
        out.writeByte(responseTag.getValue());
        out.writeBytes(vin.getBytes(ASCII_CHARSET));
        out.writeByte(EncryptionType.PLAIN.getValue());
        int lengthIndex = out.writerIndex();
        out.writeShort(0);
        FrameHeader.CheckCodeProcessor checkCodeProcessor = new FrameHeader.CheckCodeProcessor();
        out.forEachByte(startIndex, FrameHeader.HEADER_LENGTH, checkCodeProcessor);
        out.writeByte(checkCodeProcessor.getCheckCode());
    }

}
