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
    params = {"query": query}

    try:
        resp = requests.get(f"{vm_url}/api/v1/query", params=params, timeout=5)
        resp.raise_for_status()
        data = resp.json()

        # 判断是否有数据
        if data["status"] != "success" or not data["data"]["result"]:
            print("没有查询到数据")
            return None

        # 取第一个时间序列的最新点
        value = data["data"]["result"][0]["value"]
        timestamp = float(value[0])  # 秒级时间戳

        return timestamp

    except Exception as e:
        print("查询失败:", e)
        return None


if __name__ == "__main__":
    vm_url = "http://localhost:8428"
    metric = "up"

    ts = query_latest_timestamp(vm_url, metric)
    if ts:
        print("最新时间戳:", ts)
        print("格式化时间:"