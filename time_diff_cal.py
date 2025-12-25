import requests
import time

def query_latest_timestamp(vm_url, metric):
    """
    从 VictoriaMetrics 查询某个指标的最新时间戳
    :param vm_url: 例如 http://localhost:8428
    :param metric: 指标名，例如 'up' 或 'http_requests_total'
    :return: 最新时间戳（秒级），或 None
    """
    query = f"{metric}"
    params = {"query": metric, "nocache": "1", "latency_offset": "1ms"}

    try:
        resp = requests.get(f"{vm_url}/api/v1/query", params=params, timeout=5)
        resp.raise_for_status()
        data = resp.json()

        # 判断是否有数据
        if not resp.text.strip():
            print(f"Warning: 在过去 5s 内没有找到指标 '{metric_name}' 的数据")
            return None

        print(data)
        timestamp = data["data"]["result"][0]["values"][-1][0]

        return float(timestamp)

    except Exception as e:
        print("查询失败:", e)
        return None


def query_latest_value(vm_url, metric):
    """
    从 VictoriaMetrics 查询某个指标的最新时间戳
    :param vm_url: 例如 http://localhost:8428
    :param metric: 指标名，例如 'up' 或 'http_requests_total'
    :return: 最新时间戳（秒级），或 None
    """
    query = f"{metric}"
    params = {"query": metric,  "latency_offset": "1ms", "nocache": "1"}

    try:
        resp = requests.get(f"{vm_url}/api/v1/query", params=params, timeout=5)
        resp.raise_for_status()
        data = resp.json()

        # 判断是否有数据
        if not resp.text.strip():
            print(f"Warning: 在过去 5s 内没有找到指标 '{metric_name}' 的数据")
            return None
        print(data)

        value = data["data"]["result"][0]["value"][1]

        return value

    except Exception as e:
        print("查询失败:", e)
        return None

def push_metric(metric_name, value, timestamp_ms=None): # 如果不传 timestamp，则自动使用当前时间（毫秒）
    if timestamp_ms is None:
        timestamp_ms = int(time.time() * 1000)
        print(timestamp_ms)
    payload = f"{metric_name} {value}"
    resp = requests.post("http://localhost:8428/api/v1/import/prometheus", data=payload)
    resp.raise_for_status()
    print("写入成功:", payload)

if __name__ == "__main__":
    vm_url = "http://localhost:8428"
    metric = "double_metric100297[10s]"

    i = 0

    while True:
        push_metric("m_test", i)
        start_t = time.time()
        while True:
            v = query_latest_value(vm_url, "m_test")

            if v is not None and int(v) == int(i):
                print("time gap: ", time.time() - start_t)
                break
            time.sleep(0.1)
        i = i + 1
        time.sleep(0.5)

#     while True:
#         ts = query_latest_timestamp(vm_url, metric)
#         print("最新timestamp:", ts)
#         print("time gap: ", time.time() - ts)
#
#         time.sleep(0.5)