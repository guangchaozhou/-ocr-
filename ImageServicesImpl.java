package com.example.ocrhs.services.impl;

import com.example.ocrhs.commen.config.AsyncConfig;
import com.example.ocrhs.commen.config.ImgConst;
import com.example.ocrhs.commen.config.QiniuConst;
import com.example.ocrhs.commen.http.HttpResult;
import com.example.ocrhs.entity.DTO.OcrDTO;
import com.example.ocrhs.entity.PO.OcrPO;
import com.example.ocrhs.entity.VO.OcrVO;
import com.example.ocrhs.mapper.TaskMapper;
import com.example.ocrhs.services.ImageServices;
import com.example.ocrhs.utils.*;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

@Service
public class ImageServicesImpl implements ImageServices {

//    @Autowired
//    ImageUtils imageUtils;

     @Autowired
     AsyncConfig asyncConfig;

     @Autowired
     TaskMapper mapper;



    @Override
    @Async("asyncPoolTaskExecutor")
    public Future<String> ocrRequest(MultipartFile file) {

        System.out.println("ocr线程"+Thread.currentThread().getName());
        long startTime = System.currentTimeMillis();
        // 请求url 截图
        String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate";
        try {
            byte[] imgData = file.getBytes();
            String imgStr = Base64Util.encode(imgData);
            String imgParam = URLEncoder.encode(imgStr, "UTF-8");

//            String param = "id_card_side=" + "front" + "&image=" + imgParam;
            String param = "image=" + imgParam;

            // 注意这里仅为了简化编码每一次请求都去获取access_token，线上环境access_token有过期时间(一个月)， 客户端可自行缓存，过期后重新获取。
            String accessToken = GetAccessToken.getAuth();
            String result = HttpUtil.post(url, accessToken, param);
//            Thread.sleep(6000);
            long endTime = System.currentTimeMillis();
            System.out.println("请求ocr处理耗时：" + (endTime - startTime) + " 毫秒");
            return  new AsyncResult<>(result);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return  new AsyncResult<>("500");
    }

    /**
     *
     * @param result ocr解析的文字数据
     * @param userName 姓名
     * @param idcard 身份证号
     * @return
     */
    @Override
    public Integer ocrImageHealthCode(String result,String userName,String idcard) {
        System.out.println("健康码检验算法线程"+Thread.currentThread().getName());
        long startTime = System.currentTimeMillis();
        char c = userName.trim().charAt(0);
        String LastName = String.valueOf(c);
        String patternName = "." + "*" + LastName + "." + "*";
        String substring1 = idcard.substring(0, 3);
        String substring = idcard.substring(14);
        String patternIdCard1 = "." + "*" + substring1 + "." + "*";
        String patternIdCard2 = "." + "*" + substring + "." + "*";
        LocalDate now = LocalDate.now();
        String s = now.toString();
        String patternDate = "." + "*" + s + "." + "*";
        boolean mainMatches = Pattern.matches(ImgConst.PATTERN_AREA, result);
        if (mainMatches) {
            boolean nameMatches = Pattern.matches(patternName, result);
            boolean idMatchesL = Pattern.matches(patternIdCard1, result);
            boolean idMatchesR = Pattern.matches(patternIdCard2, result);
            if (nameMatches && idMatchesL && idMatchesR) {
                boolean dateMatchesR = Pattern.matches(patternDate, result);
                if(dateMatchesR){
                    boolean greenMatches = Pattern.matches(ImgConst.PATTERN_GREEN, result);
                    boolean yellowMatches = Pattern.matches(ImgConst.PATTERN_YELLOW, result);
                    if (greenMatches) {
                        long endTime = System.currentTimeMillis();
                        System.out.println("健康码验证算法处理耗时：" + (endTime - startTime) + " 毫秒");
                        return 1;
                    } else if(yellowMatches){
                        return 3;
                    }else{
                        return 4;
                    }
                }else{
                    return 8;
                }
            } else {
                return 2;
            }

        } else {
            return 7;
        }
    }

    @Override
    public Integer ocrImageTravelCode(String result, String telePhoneNumber) {
        System.out.println("行程码检验算法线程"+Thread.currentThread().getName());
        long startTime = System.currentTimeMillis();
        String mainPattern = "." + "*" + "[\\u4e00-\\u9fa5]"+"\\*" + "." + "*";
        String substring = telePhoneNumber.substring(0, 3);
        String substring2 = telePhoneNumber.substring(7);
        String patternNumber1 = "." + "*" + substring + "." + "*";
        String patternNumber2 = "." + "*" + substring2 + "." + "*";
        LocalDate now = LocalDate.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        String patternDate = "." + "*" + date + "." + "*";
        boolean mainMatches = Pattern.matches(ImgConst.PATTERN_TRIP, result);
        //处理逻辑
        if(mainMatches){
            boolean phoneMatches1 = Pattern.matches(patternNumber1, result);
            boolean phoneMatches2 = Pattern.matches(patternNumber2, result);
            if(phoneMatches1 && phoneMatches2){
                boolean dateMatches = Pattern.matches(patternDate, result);
                if(dateMatches){
                    boolean mainMatches2 = Pattern.matches(mainPattern, result);
                    if(!mainMatches2){
                        long endTime = System.currentTimeMillis();
                        System.out.println("行程码验证算法处理耗时：" + (endTime - startTime) + " 毫秒");
                        return 1;
                    }else {
                        return 5;
                    }
                }else {
                    return 8;
                }
            }else {
                return 10;
            }
        }else {
            return 7;
        }

    }

    @Override
    public Integer ocrImageNucleicAcidResults(String result, String userName, String idcard) {
        System.out.println("核酸检验算法线程"+Thread.currentThread().getName());
        long startTime = System.currentTimeMillis();
        char c = userName.trim().charAt(0);
        String LastName = String.valueOf(c);
        String patternName = "." + "*" + LastName + "." + "*";
        String substring1 = idcard.substring(0, 3);
        String substring = idcard.substring(14);
        String patternIdCard1 = "." + "*" + substring1 + "." + "*";
        String patternIdCard2 = "." + "*" + substring + "." + "*";
        boolean mainMatches = Pattern.matches(ImgConst.PATTERN_ACID, result);
        if (mainMatches) {
            boolean nameMatches = Pattern.matches(patternName, result);
            boolean idMatchesL = Pattern.matches(patternIdCard1, result);
            boolean idMatchesR = Pattern.matches(patternIdCard2, result);
            if (nameMatches && idMatchesL && idMatchesR) {
                boolean time1MatchesR = Pattern.matches(ImgConst.PATTERN_TIME1, result);
                boolean time2MatchesR = Pattern.matches(ImgConst.PATTERN_TIME2, result);
                boolean time3MatchesR = Pattern.matches(ImgConst.PATTERN_TIME3, result);
                if(time1MatchesR || time2MatchesR || time3MatchesR ){
                    boolean negativeMatches = Pattern.matches(ImgConst.PATTERN_NEGATIVE, result);
                    if (negativeMatches) {
                        long endTime = System.currentTimeMillis();
                        System.out.println("核酸验证算法处理耗时：" + (endTime - startTime) + " 毫秒");
                        return 1;
                    } else {
                        return 5;
                    }
                }else{
                    return 9;
                }
            } else {
                return 2;
            }
        } else {
            return 7;
        }
    }

    @Override
    @Async("asyncPoolTaskExecutor")
    public Future<String> uploadQiNiu(MultipartFile file) {
        System.out.println("上传七牛云线程"+Thread.currentThread().getName());
        long startTime = System.currentTimeMillis();
        try {
            //1、获取文件上传的流
            byte[] fileBytes = file.getBytes();
            //2、创建日期目录分隔
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
            String datePath = dateFormat.format(new Date());

            //3、获取文件名
            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String filename = datePath+"/"+ UUID.randomUUID().toString().replace("-", "")+ suffix;

            //4.构造一个带指定 Region 对象的配置类
            //Region.huabei(根据自己的对象空间的地址选
            Configuration cfg = new Configuration(Region.huanan());
            UploadManager uploadManager = new UploadManager(cfg);

            //5.获取七牛云提供的 token
            Auth auth = Auth.create(QiniuConst.ACCESS_KEY, QiniuConst.ACCESS_SECRET_KEY);
            String upToken = auth.uploadToken(QiniuConst.BUCKET);
            uploadManager.put(fileBytes,filename,upToken);
            long endTime = System.currentTimeMillis();
            System.out.println("上传七牛云处理耗时：" + (endTime - startTime) + " 毫秒");
            return new AsyncResult<>(QiniuConst.URL+"/"+filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new AsyncResult<>("500");
    }

    @Override
    public HttpResult addImageResult(OcrDTO dto) {
        try {
            OcrPO po = new OcrPO();
            BeanCopyUtils.copy(dto,po);
            Integer picId = mapper.getPicId(po);

            if(picId!=null){
                mapper.updateImageResult(po,picId);
                return HttpResult.ok();
            }
//            OcrVO userInFO = mapper.getUserInFO(po.getUserId().toString(), po.getSchoolId());
//            BeanCopyUtils.copy(userInFO,po);
            mapper.addImageResult(po);
        } catch (Exception e) {
            e.printStackTrace();
            return HttpResult.error("更新失败");
        }
        return HttpResult.ok();
    }
}
