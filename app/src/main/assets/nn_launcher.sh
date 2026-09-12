#!/system/bin/sh
# 嗯嗯启动器控制脚本
# 用法: nn_launcher.sh start [so路径] [卡密]
#       nn_launcher.sh stop
#       nn_launcher.sh status

SO_DIR="/data/adb"
PIDFILE="/data/adb/nn_launcher.pid"
LOGFILE="/data/adb/nn_launcher.log"

find_so() {
  want="$1"
  if [ -n "$want" ]; then
    if [ -f "$want" ]; then
      echo "$want"
      return 0
    fi
    echo "文件不存在: $want" >&2
    return 1
  fi

  n=0
  found=""
  for f in "$SO_DIR"/*.so; do
    [ -f "$f" ] || continue
    found="$f"
    n=$((n + 1))
  done

  if [ "$n" -eq 0 ]; then
    echo "未在 $SO_DIR 找到 .so 文件" >&2
    return 1
  fi
  if [ "$n" -gt 1 ]; then
    echo "$SO_DIR 下有多个 .so，请在 App 里填完整路径:" >&2
    for f in "$SO_DIR"/*.so; do echo "  $f" >&2; done
    return 1
  fi

  echo "$found"
  return 0
}

stop_existing() {
  [ -f "$PIDFILE" ] || return 0
  old=$(cat "$PIDFILE" 2>/dev/null)
  if [ -n "$old" ] && kill -0 "$old" 2>/dev/null; then
    echo "停止旧实例 PID $old"
    kill -TERM -"$old" 2>/dev/null || kill -TERM "$old" 2>/dev/null
    i=0
    while [ "$i" -lt 5 ] && kill -0 "$old" 2>/dev/null; do
      sleep 1
      i=$((i + 1))
    done
    if kill -0 "$old" 2>/dev/null; then
      kill -9 -"$old" 2>/dev/null
    fi
  fi
  rm -f "$PIDFILE"
}

do_start() {
  so_path=$(find_so "$1") || exit 1
  key="$2"

  chmod 700 "$so_path" 2>/dev/null || true

  stop_existing

  if [ -n "$key" ]; then
    setsid "$so_path" -k "$key" --record-mirror >>"$LOGFILE" 2>&1 &
  else
    setsid "$so_path" --record-mirror >>"$LOGFILE" 2>&1 &
  fi
  pid=$!
  echo "$pid" >"$PIDFILE"

  sleep 1
  if kill -0 "$pid" 2>/dev/null; then
    echo "已启动 PID $pid"
    echo "日志: $LOGFILE"
  else
    echo "启动后立即退出，日志末尾:"
    tail -n 20 "$LOGFILE" 2>/dev/null
    rm -f "$PIDFILE"
    exit 1
  fi
}

do_stop() {
  if [ ! -f "$PIDFILE" ]; then
    echo "没有记录的运行实例"
    return 0
  fi

  pid=$(cat "$PIDFILE" 2>/dev/null)
  rm -f "$PIDFILE"

  if [ -z "$pid" ]; then
    echo "PID 文件为空"
    return 0
  fi

  if kill -0 "$pid" 2>/dev/null; then
    kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
    i=0
    while [ "$i" -lt 5 ] && kill -0 "$pid" 2>/dev/null; do
      sleep 1
      i=$((i + 1))
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 -"$pid" 2>/dev/null
      echo "已强制停止 $pid"
    else
      echo "已停止 $pid"
    fi
  else
    echo "进程 $pid 已不在运行"
  fi
}

do_status() {
  pid=$(cat "$PIDFILE" 2>/dev/null)
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    echo "运行中: PID $pid"
    grep -E '^(Name|State|Pid|PPid)' "/proc/$pid/status" 2>/dev/null
    echo "线程数: $(ls /proc/$pid/task 2>/dev/null | wc -l)"
  else
    echo "未运行"
    echo "--- 系统里的 .so 进程 ---"
    ps -A -o PID,PPID,ARGS 2>/dev/null | grep -F ".so" | grep -v grep
  fi
}

cmd="$1"
[ -n "$cmd" ] || cmd="start"

case "$cmd" in
  start)  do_start "$2" "$3" ;;
  stop)   do_stop ;;
  status) do_status ;;
  *)      echo "可用命令: start | stop | status" ;;
esac

