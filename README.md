# -ocr-
基于百度ocr识别接口，使用正则表达式编写特定算法识别健康码，行程卡，核酸检测截图，运用线程池+async异步方式，配合rabiitmq解耦限流，图片识别正确率99.9%，接口平均识别时间位3000ms
自己做的项目中的一部分，ProductController为图片字符串结果生产者控制层，因为上传七牛云图床和请求百度ocr接口二者无同步关系，故采取异步解决，接口响应时间减少2000ms
消费者Consume1Controller根据图片类型解析相应的数据
具体识别算法为正则表达式匹配特定字符串，并对字符串左右数据进行相应判断处理，ImgConst类为匹配需要用到的具体字符串常量，开发者可根据需要自行更改。
