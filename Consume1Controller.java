package com.example.ocrhs.controller;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.example.ocrhs.commen.http.HttpResult;
import com.example.ocrhs.entity.DTO.OcrDTO;
import com.example.ocrhs.services.ImageServices;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RabbitListener(queues = "ocr-work1")
@Component
public class Consume1Controller {

    @Autowired
    ImageServices imageServices;

    @RabbitHandler
    public void receiveMsg(String jsonString, Channel channel, Message message) throws IOException {
        try {
            if(StrUtil.isNotBlank(jsonString)){
                ConcurrentHashMap<String,String>  hashMap= JSON.parseObject(jsonString, ConcurrentHashMap.class);
                String result = hashMap.get("result");
                String imgUrl = hashMap.get("imgUrl");
                String dtoStr = hashMap.get("dto");
                OcrDTO dto = JSON.parseObject(dtoStr, OcrDTO.class);
                //主线程
                Integer integer;
                if (dto.getType() == 1) {
                    //健康码识别
                    integer = imageServices.ocrImageHealthCode(result, dto.getUsername(), dto.getIdCard());
                    System.out.println("识别结果" + integer);
                } else if (dto.getType() == 2){
                    //行程卡识别
                    integer = imageServices.ocrImageTravelCode(result, dto.getTelePhoneNumber());
                    System.out.println("识别结果" + integer);
                } else {
                    //核酸结果识别
                    integer = imageServices.ocrImageNucleicAcidResults(result, dto.getUsername(), dto.getIdCard());
                    System.out.println("识别结果" + integer);
                }
                dto.setImageUrl(imgUrl);
                dto.setOcrStatus(integer);

                //将结果持久化到数据库
                imageServices.addImageResult(dto);
            }
            log.info("接受成功");
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()) {

                log.error("消息已重复处理失败,拒绝再次接收...");

                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false); // 拒绝消息
            } else {

                log.error("消息即将再次返回队列处理...");

                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }

    }

}
