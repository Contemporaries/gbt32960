package com.nevt.gbt32960.codec;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import com.nevt.gbt32960.paltform.LoginPlatform;
import com.nevt.gbt32960.paltform.PlatformMessage;
import com.nevt.gbt32960.type.EncryptionType;
import com.nevt.gbt32960.type.FrameHeader;
import com.nevt.gbt32960.type.RequestType;
import com.nevt.gbt32960.type.ResponseTag;
import com.nevt.gbt32960.util.ResponseMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Consumer;

import static com.nevt.gbt32960.util.GBT32960Message.*;


@Slf4j
public class GBT32960Encoder extends MessageToByteEncoder<ResponseMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ResponseMessage response, ByteBuf out) {

        RequestType requestType = response.getMessage().getRequestType();
        switch (requestType) {
            case PLATFORM_LOGIN:
                encodeMessage(out, response.getVin(), requestType, response.getMessage().getResponseTag(), buf -> encodePlatformLogin(response.getMessage(), buf));
                break;
            case PLATFORM_LOGOUT:
                encodeMessage(out, response.getVin(), requestType, response.getMessage().getResponseTag(), buf -> encodePlatformLogout(response.getMessage(), buf));
                break;
            case PLATFORM_LOGIN_RESPONSE:
                loginResponse(ctx, out, response.getVin(), response.getMessage().getResponseTag());
                break;
            case PLATFORM_LOGOUT_RESPONSE:
                logoutResponse(ctx, out, response.getVin(), response.getMessage().getResponseTag());
                break;
            default:
                loginResponse(ctx, out, response.getVin(), ResponseTag.FAILED);
                break;
        }

    }

    private void encodePlatformLogin(PlatformMessage platformMessage, ByteBuf out) {
        LoginPlatform data = platformMessage.getData();
        writeTime(out, new Date().getTime() / 1000);
        out.writeShort(data.getLoginDaySeq());
        out.writeCharSequence(data.getUsername(), ASCII_CHARSET);
        out.writeCharSequence(data.getPassword(), ASCII_CHARSET);
        out.writeByte(data.getEncryption());
    }

    private void encodePlatformLogout(PlatformMessage platformMessage, ByteBuf out) {
        LoginPlatform data = platformMessage.getData();
        writeTime(out, new Date().getTime() / 1000);
        out.writeShort(data.getLoginDaySeq());
    }


             /*SUCCESS((byte) 0x01),
    // 错误；设置未成功
    FAILED((byte) 0x02),
    // VIN重复；VIN重复错误
    VIN_DUP((byte) 0x03),
    // 命令；表示数据包为命令包，而非应答包
    COMMAND((byte) 0xFE);*/

        /*String test = "232302fe4c564256334a3042584c573031303338320101d115030f0d140d010201010000000070c4155f26a257010019700000020101043e4e204e203a157c2710080101155f26a200a20001a20d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d340d2a0d340d2a0d340d2a0d340d2a0d2a0d2a0d2a0d2a0d340d340d340d340d340d340d2a0d2a0d2a0d2a0d2a0d200d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d340d340d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d340d340d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d340d340d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d340d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d2a0d340d340d340d340d340d2a0d2a0d2a0d2a0d2a0d340d340d2a0d2a0d2a0d340d340d2a0d2a0d340d340d340d340d340d340d340d340d340d2a0d2a0d2a0d340d340d2a0d2a0d2a0d340d340d2a0d340d2a0d340d340d2a0d340d340d2a0d340d340d340d2a0d2a0d2a0d340d2a0d2a0d2a0d2a0d2a0d340d2a0d340d340d340d2a0d2a0d340d340d2a0d2a09010100303a3a3a3a39393939383838383a3939383a3b3a393a3a3a3939393938393939383838383738383837393838383838383906010e0d3401250d2001123b012437050006ec35c9025dd1330700000000000000000029";
        StringBuilder target = new StringBuilder();
        for (int i = 0; i <= test.length(); i++) {
            if (i % 2 != 0) {
                target.append(test.substring(i - 1, i + 1)).append(" ");
            }
        }
        String[] s = target.toString().split(" ");
        Byte[] bytess = Arrays.stream(s).map(n -> (byte) Integer.parseInt(n, 16)).toArray(Byte[]::new);

        byte[] bytes = new byte[bytess.length];

        for (int i = 0; i < bytess.length; i++) {
            bytes[i] = bytess[i];
        }
        out.writeBytes(bytes);
        log.info("应答帧: {}", ByteBufUtil.hexDump(out));*/

}
