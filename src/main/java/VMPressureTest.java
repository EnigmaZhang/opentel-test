import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;

public class VMPressureTest {

    static void main() throws Exception {
        Thread t1 = new Thread(() -> {
            try {
                while(true) {
                    VMPressureTest test = new VMPressureTest();
                    Instant startInstant = Instant.now();
                    for (int i = 0; i < 100; ++i) {
                        test.createMetrics(i * 1000, i * 10);
                    }
                    long timeSpent = Instant.now().toEpochMilli() - startInstant.toEpochMilli();
                    if (timeSpent > 500) {
                        System.out.println("Time spent t1: " + timeSpent);
                        System.out.println("Execute Time t1: " + LocalDateTime.now());
                    }
                    if (timeSpent < 1000L) {
                        Thread.sleep(1000L - 10L - timeSpent);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                while(true) {

                    VMPressureTest test = new VMPressureTest();
                    Instant startInstant = Instant.now();
                    for (int i = 100; i < 200; ++i) {
                        test.createMetrics(i * 1000, i * 10);
                    }
                    long timeSpent = Instant.now().toEpochMilli() - startInstant.toEpochMilli();
                    if (timeSpent > 500) {
                        System.out.println("Time spent t2: " + timeSpent);
                        System.out.println("Execute Time t2: " + LocalDateTime.now());
                    }
                    if (timeSpent < 1000L) {
                        Thread.sleep(1000L - 10L - timeSpent);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
        t1.start();
        t2.start();

        Thread.sleep(100000);
    }

    public void createMetrics(int batchCount, int resourceCount) throws Exception {
        // 包装成 ExportMetricsServiceRequest
        ExportMetricsServiceRequest.Builder request = ExportMetricsServiceRequest.newBuilder();

        // 凑齐1000个指标后发送
        for (int i = 0; i < 10; ++i) {
            Instant instant = Instant.now();
            long timestamp = instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
            Random random = new Random();
            // 构造 ScopeMetrics
            ScopeMetrics.Builder scopeMetrics = ScopeMetrics.newBuilder();

            // 每次创造100个同一资源的指标
            for (int j = 0; j < 50; ++j) {
                long long_value = random.nextLong(100L);
                double double_value = random.nextDouble() * 100;

                int longMetricNum = batchCount;
                int doubleMetricNum = batchCount + 1;
                batchCount += 2;

                // 构造一个 DataPoint，带时间戳
                NumberDataPoint longDataPoint = NumberDataPoint.newBuilder()
                        .setTimeUnixNano(timestamp)   // 时间戳（纳秒）
                        .setAsInt(long_value)           // 指标值
                        .addAttributes(KeyValue.newBuilder()
                                .setKey("description")
                                .setValue(AnyValue.newBuilder().setStringValue("long_metric" + longMetricNum).build()).build())
                        .build();
                // 构造一个 Gauge 类型的 Metric
                Metric longMetric = Metric.newBuilder()
                        .setName("long_metric" + longMetricNum)
                        .setGauge(Gauge.newBuilder().addDataPoints(longDataPoint).build())
                        .build();

                NumberDataPoint doubleDataPoint = NumberDataPoint.newBuilder()
                        .setTimeUnixNano(timestamp)
                        .setAsDouble(double_value)
                        .addAttributes(KeyValue.newBuilder()
                                .setKey("description")
                                .setValue(AnyValue.newBuilder().setStringValue("double_metric" + doubleMetricNum).build()).build())
                        .build();
                Metric doubleMetric = Metric.newBuilder()
                        .setName("double_metric" + doubleMetricNum)
                        .setGauge(Gauge.newBuilder().addDataPoints(doubleDataPoint).build())
                        .build();
                scopeMetrics.addMetrics(longMetric);
                scopeMetrics.addMetrics(doubleMetric);

            }

            // 构造 ResourceMetrics
            ResourceMetrics resourceMetrics = ResourceMetrics.newBuilder()
                    .setResource(Resource.newBuilder()
                            .addAttributes(KeyValue.newBuilder()
                                    .setKey("HOST_NAME")
                                    .setValue(AnyValue.newBuilder().setStringValue("HOST_1").build()))
                            .addAttributes(KeyValue.newBuilder()
                                    .setKey("INSTANCE_NAME")
                                    .setValue(AnyValue.newBuilder().setStringValue("INSTANCE" + resourceCount).build()))
                            .build())
                    .addScopeMetrics(scopeMetrics.build())
                    .build();
            request.addResourceMetrics(resourceMetrics);
            resourceCount += 1;
        }

        byte[] data = request.build().toByteArray();

        // POST 到 VictoriaMetrics
        URL url = new URI("http://localhost:8428/opentelemetry/v1/metrics").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-protobuf");

        try (
                OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            System.out.println("Response: " + responseCode);
        }
    }
}

