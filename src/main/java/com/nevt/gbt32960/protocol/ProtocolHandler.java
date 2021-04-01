package com.nevt.gbt32960.protocol;

import com.alibaba.fastjson.JSON;
import com.nevt.common.dto.Envelope;
import com.nevt.common.factory.ThreadFactoryCustomer;
import com.nevt.common.http.HTTPTask;
import com.nevt.gbt32960.formatters.DataUnitToDTO;
import com.nevt.gbt32960.formatters.FormatterToDataUnit;
import com.nevt.gbt32960.message.RealTimeReport;
import com.nevt.gbt32960.modle.DataUnit;
import com.nevt.gbt32960.service.PlatformService;
import com.nevt.gbt32960.service.RedisService;
import com.nevt.gbt32960.paltform.LoginPlatform;
import com.nevt.gbt32960.paltform.PlatformMessage;
import com.nevt.gbt32960.type.FrameHeader;
import com.nevt.gbt32960.type.RequestType;
import com.nevt.gbt32960.type.ResponseTag;
import com.nevt.gbt32960.util.GBT32960Message;
import com.nevt.gbt32960.util.ResponseMessage;
import com.nevt.gbt32960.util.SpringUtil;
import com.nevt.gbt32960.formatters.TimeFormat;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

@Slf4j
@ChannelHandler.Sharable
public class ProtocolHandler extends ChannelDuplexHandler {

    private static final String KEY = "PLATFORM";

    private static final String URL = "http://nevt05:30201/api/input";

    private static final ExecutorService EXECUTOR_SERVICE = ThreadFactoryCustomer.defaultExecutorService();

    @Getter
    private static final ProtocolHandler instance = new ProtocolHandler();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        response(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                ctx.close();
            }
        }

    }

    private void platformLoginResponse(ChannelHandlerContext context, Object msg) {
        GBT32960Message message = toGBT32960Message(msg);

        FrameHeader header = message.getHeader();
        log.info("header: ==> " + header);

        if (redisService().isKey(KEY, header.getVin())) {
            vinRepeat(context, header);
            log.info("VIN Repeat");
        } else {
            LoginPlatform loginPlatform = (LoginPlatform) message.getDataUnit();

            String username = loginPlatform.getUsername();
            String password = loginPlatform.getPassword();
            boolean one = platformService().findOne(username, password);
            if (one) {
                redisService().add(header.getVin(), loginPlatform.getLoginDaySeq());

                context.writeAndFlush(responseMessage(header.getVin(), RequestType.PLATFORM_LOGIN_RESPONSE, ResponseTag.SUCCESS));

                log.info("Platform login success! login time: ==> "
                        + TimeFormat.longTimeToZoneDateTime(loginPlatform.getLoginTime()));
            } else {
                context.writeAndFlush(responseMessage(header.getVin(), RequestType.PLATFORM_LOGIN_RESPONSE, ResponseTag.FAILED));
                log.trace("Platform username or password error!");
            }
        }

    }

    private void vinRepeat(ChannelHandlerContext ctx, FrameHeader header) {
        ctx.writeAndFlush(responseMessage(header.getVin(), RequestType.PLATFORM_LOGIN_RESPONSE, ResponseTag.VIN_DUP));
    }

    private GBT32960Message toGBT32960Message(Object msg) {
        if (msg instanceof GBT32960Message) {
            return (GBT32960Message) msg;
        }
        throw new ClassCastException("msg not conversion to GBT32960Message");
    }

    private void response(ChannelHandlerContext context, Object msg) {
        GBT32960Message message = toGBT32960Message(msg);
        FrameHeader header = message.getHeader();
        switch (header.getRequestType()) {
            case REISSUE:
            case REAL_TIME:
                EXECUTOR_SERVICE.execute(() -> {
                    RealTimeReport realTimeReport = (RealTimeReport) message.getDataUnit();
                    DataUnit dataUnit = FormatterToDataUnit.RealTimeReportToDataUnit(realTimeReport, header);
                    Envelope envelope = dataUnitToDTO().toDTO(dataUnit);
//                send(envelope);
                    log.info("envelope: ==> " + envelope);
                });
                break;
            case PLATFORM_LOGIN:
                platformLoginResponse(context, msg);
                break;
            case PLATFORM_LOGOUT:
                platformLogoutResponse(context, msg);
                break;
            case PLATFORM_LOGOUT_RESPONSE:
            case PLATFORM_LOGIN_RESPONSE:
                log.info(header.getResponseTag().name());
            default:
                log.info("no request type");
                break;
        }
    }

    private void platformLogoutResponse(ChannelHandlerContext context, Object msg) {
        GBT32960Message message = toGBT32960Message(msg);
        LoginPlatform dataUnit = (LoginPlatform) message.getDataUnit();
        String vin = message.getHeader().getVin();
        if (redisService().isKey(KEY, vin)) {

            boolean delete = redisService().delete(vin, dataUnit.getLoginDaySeq());

            if (delete) {
                log.info("Platform logout success!");
                context.writeAndFlush(context.writeAndFlush(responseMessage(vin, RequestType.PLATFORM_LOGOUT_RESPONSE, ResponseTag.SUCCESS)));
            } else {
                log.info("Platform logout fail! 登入与登出序列化不匹配!");
                context.writeAndFlush(context.writeAndFlush(responseMessage(vin, RequestType.PLATFORM_LOGOUT_RESPONSE, ResponseTag.FAILED)));
            }

        } else {
            context.writeAndFlush(responseMessage(vin, RequestType.PLATFORM_LOGOUT_RESPONSE, ResponseTag.FAILED));
            log.info("未登录");
        }
    }

    private ResponseMessage responseMessage(String vin, RequestType requestType, ResponseTag responseTag) {
        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setVin(vin);
        PlatformMessage platformMessage = new PlatformMessage();
        platformMessage.setRequestType(requestType);
        platformMessage.setResponseTag(responseTag);
        platformMessage.setData(null);
        responseMessage.setMessage(platformMessage);
        return responseMessage;
    }

    private PlatformService platformService() {
        return SpringUtil.getBean(PlatformService.class);
    }

    private RedisService redisService() {
        return SpringUtil.getBean(RedisService.class);
    }

    private DataUnitToDTO dataUnitToDTO() {
        return SpringUtil.getBean(DataUnitToDTO.class);
    }

    private void send(Envelope envelope) {
        HTTPTask.newBuilder().setUrl(URL).build().send(envelope);
    }

}
