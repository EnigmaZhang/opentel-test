import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.*;
import java.util.Collection;
import java.util.List;

public class MonitorIndicatorProcessor {

    public static void main() throws InterruptedException, IOException {
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

//        MonitorIndicatorProcessor processor = new MonitorIndicatorProcessor();
//        processor.processIndicator("CATE_1_11", "123456", "123.456");
    }

     public void processIndicator(String instance, String indicatorCode, Object indicatorValue) throws InterruptedException {
        // 模拟已有数据列表
        List<MyMetric> myMetrics = List.of(
                new MyMetric(42, System.currentTimeMillis() - 5000),
                new MyMetric(55, System.currentTimeMillis() - 3000),
                new MyMetric(60, System.currentTimeMillis())
        );

        // 自定义 Exporter：只打印，不传输
        MetricExporter consoleExporter = new MetricExporter() {
            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                return null;
            }

            @Override
            public CompletableResultCode export(Collection<MetricData> metrics) {
                for (MetricData metric : metrics) {
                    System.out.println(metric.getDoubleGaugeData().getPoints().stream().findAny().get().getClass());
                    PointData pointData = metric.getDoubleGaugeData().getPoints().stream().findAny().get();
                    System.out.println(metric.getDoubleGaugeData().getPoints().stream().findAny().get()
                            .getEpochNanos());
                    Class<?> clazz = pointData.getClass();
                    try {
                        Field nanoFiled = clazz.getDeclaredField("epochNanos");
                        nanoFiled.setAccessible(true);
                        Instant instant = ZonedDateTime.of(2025, 12, 4, 13, 13, 13, 123000000,
                                ZoneId.systemDefault()).toInstant(); // 纳秒
                        nanoFiled.set(pointData, instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("本地指标: " + metric.getName() + " -> " + metric);
                }
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.ofSuccess();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.ofSuccess();
            }
        };

        // 创建 MeterProvider，注册自定义 Exporter
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(
                        PeriodicMetricReader.builder(consoleExporter)
                                .setInterval(Duration.ofSeconds(5))
                                .build()
                )
                .build();

        Meter meter = meterProvider.get("instance");
        // 使用回调，每个指标都可以放入数值

        ObservableDoubleGauge gauge = meter.gaugeBuilder("indicatorCode")
                .setDescription("带指标时间戳")
                .setUnit("1")
                .buildWithCallback(measurement -> {
                    for (MyMetric m : myMetrics) {
                        // 把时间戳作为标签存储，保证不会丢失
                        measurement.record(m.value,
                                Attributes.of(AttributeKey.stringKey("ts"), String.valueOf(m.timestamp)));
                    }
                });

        Thread.sleep(15000);
    }

    // 模拟已有指标数据
    static class MyMetric {
        final double value;
        final long timestamp;
        MyMetric(double value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
