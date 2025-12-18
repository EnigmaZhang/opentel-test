# 使用OTel记录指标

## 当前设计目标

目前先设想了简单的指标场景， 假设OMPBUSIR已经收集的每个指标包括以下四个属性：时间戳、实例名、指标名、指标值，
目标是将指标存储到VictoriaMetrics数据库中，并在Grafana展示。

## 目前技术方案

### 构造OTLP对象

首先要把指标转换为OTLP格式的对象，使用opentelemetry-proto依赖中定义的类:

对应的层次结构如下：MetricsData → ResourceMetrics → ScopeMetrics → Metric → DataPoint

构造ResourceMetrics类， 此类代表指标对应的资源，构造时填入对应的资源键值对（即**实例名**）

ResourceMetrics中添加构造的ScopeMetrics对象，表示该资源下由某个采集库/SDK 产生的指标集合。

ScopeMetrics对象下添加若干个构造的Metric对象，表示集合下对应的指标，在此处设定**指标名**。

Metric对象中添加对应的DataPoint，此处DataPoint类型为Gauge（表示某一时刻的数值快照），在DataPoint中设定**指标值**和**时间戳**，
以及其他的标签键值对

最后把ResourceMetrics类包装成ExportMetricsServiceRequest类，用于后续序列化。

### 发送到VictoriaMetrics

使用ExportMetricsServiceRequest对象的toByteArray() 得到 Protobuf 二进制

向VictoriaMetrics的/opentelemetry/v1/metrics接口发送POST请求，Content-Type为application/x-protobuf，
写入对应的二进制，即可在数据库中写入指标。

### Grafana展示

由于VictoriaMetrics兼容Prometheus格式数据查询，因此当作Prometheus添加数据源即可在Grafana上展示数据

## 当前问题

1. DataPoint数据类型只支持浮点和整数，不支持字符串，对于字符串类型只能通过标签键值对的方式写入，对于后续指标计算的影响需要评估

2. 大量向VM通过POST写数据是否性能开销过大，是否需要缓存或者中间件机制（例如OpenTelemetry Collector）

3. 乱序时间戳向VM写入，对于数据分析性能和展示效果是否有影响

```java
        // 模拟已有指标数据
        Instant instant = ZonedDateTime.of(2025, 12, 9, 16, 16, 18, 123000000,
                ZoneId.systemDefault()).toInstant(); // 纳秒
        long timestamp = instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
        long value = 3000;

        // 构造一个 DataPoint，带时间戳
        NumberDataPoint dataPoint = NumberDataPoint.newBuilder()
                .setTimeUnixNano(timestamp)   // 时间戳（纳秒）
                .setAsInt(value)           // 指标值
                .addAttributes(KeyValue.newBuilder()
                        .setKey("description")
                        .setValue(AnyValue.newBuilder().setStringValue("委托量").build()).build())
                .build();

        NumberDataPoint indexPoint = NumberDataPoint.newBuilder()
                .setTimeUnixNano(timestamp)
                .setAsDouble(299.20)
                .addAttributes(KeyValue.newBuilder()
                        .setKey("description")
                        .setValue(AnyValue.newBuilder().setStringValue("指数").build()).build())
                .build();

        // 构造一个 Gauge 类型的 Metric
        Gauge gaugeMetric = Gauge.newBuilder()
                .addDataPoints(dataPoint)
                .build();

        Metric metric = Metric.newBuilder()
                .setName("order_num")
                .setUnit("")
                .setGauge(gaugeMetric)
                .build();

        Metric metric2 = Metric.newBuilder()
                .setName("index_value")
                .setGauge(Gauge.newBuilder().addDataPoints(indexPoint).build())
                .build();

        // 构造 ScopeMetrics
        ScopeMetrics scopeMetrics = ScopeMetrics.newBuilder()
                .addMetrics(metric)
                .addMetrics(metric2)
                .build();

        // 构造 ResourceMetrics
        ResourceMetrics resourceMetrics = ResourceMetrics.newBuilder()
                .setResource(Resource.newBuilder()
                        .addAttributes(KeyValue.newBuilder()
                                .setKey("HOST_NAME")
                                .setValue(AnyValue.newBuilder().setStringValue("OMPWEB-1").build()))
                        .addAttributes(KeyValue.newBuilder()
                                .setKey("INSTANCE_NAME")
                                .setValue(AnyValue.newBuilder().setStringValue("CATE_1").build()))
                        .build())
                .addScopeMetrics(scopeMetrics)
                .build();

        // 序列化为 Protobuf 二进制
        byte[] otlpBytes = resourceMetrics.toByteArray();
        System.out.println("OTLP Metric 序列化成功，字节长度: " + otlpBytes.length);
        // 打印文本格式（调试用）
        System.out.println(resourceMetrics);

        // 包装成 ExportMetricsServiceRequest
        ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(resourceMetrics)
                .build();

        // 序列化为 Protobuf 二进制
        byte[] data = request.toByteArray();

        // POST 到 VictoriaMetrics
        URL url = new URL("http://localhost:8428/opentelemetry/v1/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-protobuf");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }

        int responseCode = conn.getResponseCode();
        System.out.println("Response: " + responseCode);
```