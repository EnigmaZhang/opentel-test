while true; do
    echo "$(date '+%Y-%m-%d %H:%M:%S') | $(curl -s http://localhost:8428/metrics | grep vm_rows_inserted_total | grep opentel)" >> ./vm_row.log
    sleep 2
done
