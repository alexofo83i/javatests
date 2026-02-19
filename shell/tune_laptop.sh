#!/bin/bash
# benchmark_mode.sh - Оптимизированная версия с timestamped backups

set -e

# Создаем уникальный ID для этой сессии
SESSION_ID=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/tmp/benchmark_backup_${SESSION_ID}"
mkdir -p "$BACKUP_DIR"

echo "=================================="
echo "Включение режима бенчмарков"
echo "=================================="
echo "Session ID: $SESSION_ID"
echo "Backup directory: $BACKUP_DIR"

# Сохраняем состояние сервисов
echo "Сохраняю состояние сервисов..."
systemctl list-units --type=service --state=running | grep -E "\.service" | awk '{print $1}' > "$BACKUP_DIR/running_services.txt"

# Сохраняем CPU настройки
if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor ]; then
    cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor > "$BACKUP_DIR/cpu_governor"
fi

# Сохраняем настройки Turbo Boost
if [ -f /sys/devices/system/cpu/intel_pstate/no_turbo ]; then
    cat /sys/devices/system/cpu/intel_pstate/no_turbo > "$BACKUP_DIR/intel_no_turbo"
fi

# Сохраняем I/O scheduler для всех дисков
for disk in /sys/block/*/queue/scheduler; do
    if [ -f "$disk" ]; then
        disk_name=$(echo $disk | cut -d/ -f4)
        # Сохраняем текущий scheduler (в квадратных скобках)
        current_scheduler=$(cat $disk | awk '{print $NF}' | sed 's/[][]//g')
        echo "$current_scheduler" > "$BACKUP_DIR/scheduler_${disk_name}"
        
        # Сохраняем все доступные schedulers для информации
        all_schedulers=$(cat $disk | sed 's/\[//g; s/\]//g')
        echo "$all_schedulers" > "$BACKUP_DIR/scheduler_${disk_name}_available"
    fi
done

# Сохраняем дополнительные I/O параметры
for disk in /sys/block/*; do
    if [ -d "$disk" ]; then
        disk_name=$(basename $disk)
        # Сохраняем nr_requests
        if [ -f "$disk/queue/nr_requests" ]; then
            cat "$disk/queue/nr_requests" > "$BACKUP_DIR/${disk_name}_nr_requests"
        fi
        # Сохраняем read_ahead_kb
        if [ -f "$disk/queue/read_ahead_kb" ]; then
            cat "$disk/queue/read_ahead_kb" > "$BACKUP_DIR/${disk_name}_read_ahead_kb"
        fi
        # Сохраняем nomerges
        if [ -f "$disk/queue/nomerges" ]; then
            cat "$disk/queue/nomerges" > "$BACKUP_DIR/${disk_name}_nomerges"
        fi
        # Сохраняем add_random
        if [ -f "$disk/queue/add_random" ]; then
            cat "$disk/queue/add_random" > "$BACKUP_DIR/${disk_name}_add_random"
        fi
    fi
done

# Сохраняем ключевые sysctl параметры
sysctl_params=(
    "vm.swappiness"
    "vm.dirty_ratio"
    "vm.dirty_background_ratio"
    "vm.vfs_cache_pressure"
    "vm.dirty_writeback_centisecs"
    "kernel.numa_balancing"
    "kernel.sched_autogroup_enabled"
    "net.core.rmem_max"
    "net.core.wmem_max"
    "net.ipv4.tcp_rmem"
    "net.ipv4.tcp_wmem"
    "net.core.default_qdisc"
    "net.ipv4.tcp_congestion_control"
)

for param in "${sysctl_params[@]}"; do
    value=$(sysctl -n $param 2>/dev/null || echo "NOT_SET")
    # Сохраняем в файл, заменяя точки на подчеркивания для имени файла
    echo "$value" > "$BACKUP_DIR/sysctl_${param//./_}"
done

# Сохраняем информацию о системе для диагностики
{
    echo "Backup created: $(date)"
    echo "Hostname: $(hostname)"
    echo "Kernel: $(uname -r)"
    echo "CPU: $(lscpu | grep 'Model name' | head -1)"
    echo "Memory: $(free -h | grep Mem)"
} > "$BACKUP_DIR/system_info.txt"

# Сохраняем текущий runlevel
systemctl get-default > "$BACKUP_DIR/default_target"

# Сохраняем статус графической сессии
if [ -n "$DISPLAY" ] || [ -n "$WAYLAND_DISPLAY" ]; then
    echo "graphical" > "$BACKUP_DIR/session_type"
else
    echo "console" > "$BACKUP_DIR/session_type"
fi

echo "=================================="
echo "1. Отключение фоновых сервисов"
echo "=================================="

disable_services_by_pattern() {
    local pattern=$1
    local services=$(systemctl list-unit-files --type=service | grep -i "$pattern" | awk '{print $1}' || true)
    
    for service in $services; do
        if systemctl is-active --quiet $service 2>/dev/null; then
            echo "Останавливаю: $service"
            sudo systemctl stop $service 2>/dev/null || true
        fi
    done
}

critical_patterns=(
    "snapd"
    "apt-daily"
    "packagekit"
    "cron"
    "anacron"
    "man-db"
    "apport"
    "power-profiles-daemon"
    "thermald"
)

for pattern in "${critical_patterns[@]}"; do
    disable_services_by_pattern "$pattern"
done

echo "=================================="
echo "2. Настройка CPU"
echo "=================================="

# Устанавливаем производительный governor
echo "Установка performance governor"
if command -v cpupower &> /dev/null; then
    sudo cpupower frequency-set -g performance
else
    echo performance | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor
fi

# НЕ отключаем Turbo Boost - он нужен для максимальной производительности
echo "Turbo Boost оставлен включенным (для максимальной производительности)"

# Фиксируем частоту на максимуме для стабильности
for cpu in /sys/devices/system/cpu/cpu*/cpufreq; do
    if [ -f "$cpu/scaling_max_freq" ] && [ -f "$cpu/cpuinfo_max_freq" ]; then
        max_freq=$(cat "$cpu/cpuinfo_max_freq")
        echo $max_freq | sudo tee "$cpu/scaling_max_freq" > /dev/null
        echo $max_freq | sudo tee "$cpu/scaling_min_freq" > /dev/null
    fi
done

# Отключаем энергосберегающие C-state (для минимальной латентности)
echo "Отключение глубоких C-state"
sudo cpupower idle-set -D 1 2>/dev/null || true

echo "=================================="
echo "3. Настройка I/O"
echo "=================================="

# Оптимизация для NVMe
for disk in /sys/block/nvme*; do
    if [ -d "$disk" ]; then
        disk_name=$(basename $disk)
        echo "Настройка NVMe $disk_name"
        
        # Используем none (noop) для NVMe
        echo "none" | sudo tee "$disk/queue/scheduler" > /dev/null
        
        # Оптимизация для максимальной производительности
        echo 2048 | sudo tee "$disk/queue/nr_requests" > /dev/null
        echo 2 | sudo tee "$disk/queue/nomerges" > /dev/null
        echo 4096 | sudo tee "$disk/queue/read_ahead_kb" > /dev/null
        echo 0 | sudo tee "$disk/queue/add_random" > /dev/null
    fi
done

# Оптимизация для SATA SSD
for disk in /sys/block/sd*; do
    if [ -d "$disk" ] && [ -f "$disk/queue/rotational" ]; then
        if [ "$(cat $disk/queue/rotational)" -eq 0 ]; then
            disk_name=$(basename $disk)
            echo "Настройка SSD $disk_name"
            echo "mq-deadline" | sudo tee "$disk/queue/scheduler" > /dev/null
            echo 512 | sudo tee "$disk/queue/nr_requests" > /dev/null
            echo 1024 | sudo tee "$disk/queue/read_ahead_kb" > /dev/null
        fi
    fi
done

echo "=================================="
echo "4. Настройка памяти"
echo "=================================="

# Проверяем, есть ли достаточно свободной памяти перед отключением swap
mem_total=$(free -g | awk '/^Mem:/{print $2}')
swap_total=$(free -g | awk '/^Swap:/{print $2}')

if [ $swap_total -gt 0 ] && [ $mem_total -lt 16 ]; then
    echo "Предупреждение: мало RAM ($mem_total GB), swap может быть нужен"
    read -p "Все равно отключить swap? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        sudo swapoff -a
        echo "swap_off" > "$BACKUP_DIR/swap_status"
    else
        echo "swap_kept" > "$BACKUP_DIR/swap_status"
    fi
else
    sudo swapoff -a
    echo "swap_off" > "$BACKUP_DIR/swap_status"
fi

# Оптимизированные параметры VM
sudo sysctl -w vm.swappiness=10
sudo sysctl -w vm.dirty_ratio=30
sudo sysctl -w vm.dirty_background_ratio=5
sudo sysctl -w vm.vfs_cache_pressure=200
sudo sysctl -w vm.dirty_writeback_centisecs=1500
sudo sysctl -w kernel.numa_balancing=0
sudo sysctl -w vm.nr_hugepages=0

echo "=================================="
echo "5. Настройка сети"
echo "=================================="

# Оптимизация сети для минимальной задержки
sudo sysctl -w net.core.rmem_max=134217728
sudo sysctl -w net.core.wmem_max=134217728
sudo sysctl -w net.ipv4.tcp_rmem="4096 87380 134217728"
sudo sysctl -w net.ipv4.tcp_wmem="4096 65536 134217728"
sudo sysctl -w net.core.default_qdisc=fq
sudo sysctl -w net.ipv4.tcp_congestion_control=bbr

echo "=================================="
echo "6. Настройка графики (опционально)"
echo "=================================="

# Для NVIDIA GPU (если есть)
if command -v nvidia-smi &> /dev/null; then
    echo "Настройка NVIDIA GPU для производительности"
    # Сохраняем текущие настройки NVIDIA
    nvidia-smi -q -d PERFORMANCE | grep -A 10 "Performance State" > "$BACKUP_DIR/nvidia_performance.txt"
    nvidia-smi -pm 1
    nvidia-smi -ac 5001,1590 2>/dev/null || true
fi

echo "=================================="
echo "Настройка завершена!"
echo "=================================="
echo ""
echo "Session ID: $SESSION_ID"
echo "Backup directory: $BACKUP_DIR"
echo ""
echo "Проверка текущих настроек:"
echo "---------------------------"
echo "CPU Governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo 'N/A')"
echo "CPU Frequency: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null | awk '{printf "%.2f GHz", $1/1000000}' || echo 'N/A')"
echo "I/O Scheduler (NVMe): $(cat /sys/block/nvme*/queue/scheduler 2>/dev/null | head -1 || echo 'N/A')"
echo "Swap: $(swapon --show | wc -l) devices active"
echo ""
echo "Для восстановления выполните:"
echo "sudo ./restore_benchmark.sh $SESSION_ID"
echo ""
echo "Если забудете ID, посмотрите доступные бэкапы:"
echo "ls -la /tmp/benchmark_backup_*"
