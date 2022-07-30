package com.example.ocrhs.controller;

import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSON;
import com.example.ocrhs.commen.config.AsyncConfig;
import com.example.ocrhs.commen.http.HttpResult;
import com.example.ocrhs.commen.singleton.ConcurrentHashMapSingleton;
import com.example.ocrhs.entity.DTO.OcrDTO;
import com.example.ocrhs.services.ImageServices;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import io.swagger.annotations.Api;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RestController
@Api("生产者")
@RequestMapping("/api/image")
public class ProductController{

    @Autowired
    ImageServices imageServices;

    @Autowired
    AsyncConfig asyncConfig;

    @Autowired
    RabbitTemplate rabbitTemplate;


       @PostMapping("/upload")
//       @RequiresAuthentication
       public HttpResult testRobbitmqProduct(MultipartFile file, OcrDTO dto){

           long startTime = System.currentTimeMillis();
           //获取文件名
           String fileName = file.getOriginalFilename();
           //获取文件类型
           String fileType = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
           if (!"jpg".equals(fileType) && !"jpeg".equals(fileType) && !"png".equals(fileType) && !"bmp".equals(fileType)) {
               return HttpResult.error("文件类型不符合要求!");
           }

           try {
               //ocr异步请求百度ocr识别字符串子线程
               Future<String> stringFuture = imageServices.ocrRequest(file);

               //上传qiNiu云图床异步子线程
               Future<String> uploadQiNiu = imageServices.uploadQiNiu(file);


               //设置超时时间，子线程五秒无法返回结果则抛出异常结束应用
               String result = stringFuture.get(17, TimeUnit.SECONDS);
               String imgUrl = uploadQiNiu.get(17, TimeUnit.SECONDS);


//
               ConcurrentHashMap<String, String> map = ConcurrentHashMapSingleton.getSingleton();
               map.put("result",result);
               map.put("imgUrl",imgUrl);
               String dtoString = JSON.toJSONString(dto);
               map.put("dto",dtoString);

               String jsonString = JSON.toJSONString(map);
               rabbitTemplate.convertAndSend("ocr-work","",jsonString);

               //生产者到交换机是否成功
               rabbitTemplate.setConfirmCallback((correlationData, ack, failCause)->{
//                   String id = correlationData.getId();
                   if(ack){
                       log.info("消息发送成功");
                   }else{
                       log.info("消息发送失败"+correlationData,ack,failCause);
                   }

               });

               //设置交换机处理失败消息的处理模式 如果消息没有路由到queue,则返回消息发送方法
               rabbitTemplate.setMandatory(true);
               //交换机路由到queue是否成功
               rabbitTemplate.setReturnsCallback(returned -> log.info("路由到queue失败", returned));

               long endTime = System.currentTimeMillis();
               System.out.println("最终处理耗时：" + (endTime - startTime) + " 毫秒");
               return HttpResult.ok();
           } catch (InterruptedException e) {
               log.error("异步错误----------->"+e);
               return HttpResult.error();
           } catch (ExecutionException e) {
               log.error("图片上传错误----------->"+e);
               return HttpResult.error();
           } catch (TimeoutException e) {
               log.error("请求超时！请稍等一段时间重新提交"+e);
               return HttpResult.ok("请求超时！请稍等一段时间重新提交");
           }
       }

//    @Override
//    public void confirm(CorrelationData correlationData, boolean b, String s) {
//        if (!b) {
//            log.error("消息发送异常!");
//        } else {
//            log.info("发送者爸爸已经收到确认，correlationData={} ,ack={}, cause={}", correlationData.getId(), b, s);
//        }
//    }
}
