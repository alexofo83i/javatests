#!/bin/bash
# restore_benchmark.sh - Восстановление с timestamped backups

set -e

# Функция для отображения помощи
show_help() {
    echo "Использование: $0 [SESSION_ID]"
    echo ""
    echo "Восстанавливает систему из бэкапа, созданного benchmark_mode.sh"
    echo ""
    echo "Аргументы:"
    echo "  SESSION_ID    ID сессии в формате YYYYMMDD_HHMMSS"
    echo "                Если не указан, показывает список доступных бэкапов"
    echo ""
    echo "Примеры:"
    echo "  $0                     # показать список бэкапов"
    echo "  $0 20240219_153045     # восстановить из конкретного бэкапа"
    echo "  $0 latest              # восстановить из самого свежего бэкапа"
    exit 0
}

# Функция для выбора бэкапа
select_backup() {
    local backups=($(ls -d /tmp/benchmark_backup_* 2>/dev/null | sort))
    
    if [ ${#backups[@]} -eq 0 ]; then
        echo "❌ Бэкапы не найдены в /tmp/benchmark_backup_*"
        exit 1
    fi
    
    echo "Доступные бэкапы:"
    echo "------------------"
    for i in "${!backups[@]}"; do
        backup_name=$(basename "${backups[$i]}")
        backup_id=${backup_name#benchmark_backup_}
        
        # Показываем информацию о бэкапе
        if [ -f "${backups[$i]}/system_info.txt" ]; then
            created=$(grep "Backup created:" "${backups[$i]}/system_info.txt" | cut -d: -f2-)
            echo "$((i+1)). $backup_id - $created"
        else
            echo "$((i+1)). $backup_id"
        fi
    done
    
    echo ""
    read -p "Выберите номер бэкапа для восстановления (или q для выхода): " choice
    
    if [[ "$choice" == "q" ]]; then
        exit 0
    fi
    
    if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "${#backups[@]}" ]; then
        BACKUP_DIR="${backups[$((choice-1))]}"
        SESSION_ID=$(basename "$BACKUP_DIR" | sed 's/benchmark_backup_//')
    else
        echo "❌ Неверный выбор"
        exit 1
    fi
}

# Проверка аргументов
if [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    show_help
fi

# Определяем бэкап для восстановления
if [ -n "$1" ]; then
    if [ "$1" == "latest" ]; then
        # Находим самый свежий бэкап
        LATEST=$(ls -d /tmp/benchmark_backup_* 2>/dev/null | sort -r | head -1)
        if [ -n "$LATEST" ]; then
            BACKUP_DIR="$LATEST"
            SESSION_ID=$(basename "$BACKUP_DIR" | sed 's/benchmark_backup_//')
        else
            echo "❌ Бэкапы не найдены"
            exit 1
        fi
    else
        # Пробуем использовать указанный ID
        SESSION_ID="$1"
        BACKUP_DIR="/tmp/benchmark_backup_${SESSION_ID}"
        if [ ! -d "$BACKUP_DIR" ]; then
            echo "❌ Бэкап не найден: $BACKUP_DIR"
            select_backup
        fi
    fi
else
    select_backup
fi

echo "=================================="
echo "Восстановление системы"
echo "=================================="
echo "Session ID: $SESSION_ID"
echo "Backup directory: $BACKUP_DIR"
echo ""

# Проверяем существование бэкапа
if [ ! -d "$BACKUP_DIR" ]; then
    echo "❌ Ошибка: директория бэкапа не найдена"
    exit 1
fi

echo "=================================="
echo "1. Восстановление CPU"
echo "=================================="

# Восстанавливаем governor
if [ -f "$BACKUP_DIR/cpu_governor" ]; then
    gov=$(cat "$BACKUP_DIR/cpu_governor")
    echo "Восстанавливаю governor = $gov"
    echo $gov | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2>/dev/null || true
fi

# Восстанавливаем Turbo Boost
if [ -f "$BACKUP_DIR/intel_no_turbo" ]; then
    noturbo=$(cat "$BACKUP_DIR/intel_no_turbo")
    echo "Восстанавливаю Turbo Boost = $noturbo"
    echo $noturbo | sudo tee /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || true
fi

# Снимаем фиксацию частоты
for cpu in /sys/devices/system/cpu/cpu*/cpufreq; do
    if [ -f "$cpu/scaling_min_freq" ] && [ -f "$cpu/cpuinfo_min_freq" ]; then
        min_freq=$(cat "$cpu/cpuinfo_min_freq")
        echo $min_freq | sudo tee "$cpu/scaling_min_freq" > /dev/null 2>&1 || true
    fi
done

# Включаем C-state обратно
sudo cpupower idle-set -E 2>/dev/null || true

echo "=================================="
echo "2. Восстановление I/O"
echo "=================================="

# Восстанавливаем scheduler для всех дисков
for backup in "$BACKUP_DIR"/scheduler_*; do
    if [ -f "$backup" ] && [[ "$backup" != *"_available" ]]; then
        disk_name=$(basename "$backup" | sed 's/scheduler_//')
        scheduler=$(cat "$backup")
        if [ -e "/sys/block/$disk_name/queue/scheduler" ]; then
            echo "Восстанавливаю $disk_name scheduler = $scheduler"
            echo $scheduler | sudo tee "/sys/block/$disk_name/queue/scheduler" > /dev/null 2>&1 || true
        fi
    fi
done

# Восстанавливаем I/O параметры
for param in nr_requests read_ahead_kb nomerges add_random; do
    for backup in "$BACKUP_DIR"/*"${param}"*; do
        if [ -f "$backup" ]; then
            filename=$(basename "$backup")
            disk_name=$(echo "$filename" | sed "s/_${param}//")
            value=$(cat "$backup")
            
            if [ -e "/sys/block/$disk_name/queue/$param" ]; then
                echo "Восстанавливаю $disk_name/$param = $value"
                echo $value | sudo tee "/sys/block/$disk_name/queue/$param" > /dev/null 2>&1 || true
            fi
        fi
    done
done

echo "=================================="
echo "3. Восстановление памяти"
echo "=================================="

# Восстанавливаем swap если был отключен
if [ -f "$BACKUP_DIR/swap_status" ] && [ "$(cat "$BACKUP_DIR/swap_status")" == "swap_off" ]; then
    if [ $(swapon --show | wc -l) -eq 0 ]; then
        echo "Включаю swap"
        sudo swapon -a
    fi
fi

# Восстанавливаем sysctl параметры
for backup in "$BACKUP_DIR"/sysctl_*; do
    if [ -f "$backup" ]; then
        # Конвертируем имя файла обратно в параметр sysctl
        param_name=$(basename "$backup" | sed 's/sysctl_//' | tr '_' '.')
        value=$(cat "$backup")
        
        if [ "$value" != "NOT_SET" ]; then
            echo "Восстанавливаю $param_name = $value"
            sudo sysctl -w "$param_name=$value" 2>/dev/null || true
        fi
    fi
done

echo "=================================="
echo "4. Запуск сервисов"
echo "=================================="

# Запускаем базовые сервисы
services_to_start=(
    "power-profiles-daemon"
    "thermald"
    "cron"
    "snapd"
    "anacron"
)

for service in "${services_to_start[@]}"; do
    if systemctl list-unit-files | grep -q "$service"; then
        echo "Запускаю $service"
        sudo systemctl start $service 2>/dev/null || true
        sudo systemctl enable $service 2>/dev/null || true
    fi
done

# Восстанавливаем NVIDIA если была
if [ -f "$BACKUP_DIR/nvidia_performance.txt" ] && command -v nvidia-smi &> /dev/null; then
    echo "Восстанавливаю NVIDIA настройки"
    sudo nvidia-smi -pm 0
    sudo nvidia-smi -rac
fi

echo "=================================="
echo "5. Восстановление GUI"
echo "=================================="

# Восстанавливаем target если был изменен
if [ -f "$BACKUP_DIR/default_target" ]; then
    default_target=$(cat "$BACKUP_DIR/default_target")
    current_target=$(systemctl get-default)
    
    if [ "$default_target" != "$current_target" ]; then
        echo "Восстанавливаю default target = $default_target"
        sudo systemctl set-default "$default_target"
        
        # Если были в multi-user и была графическая сессия, переключаем обратно
        if [ "$default_target" == "graphical.target" ] && [ -f "$BACKUP_DIR/session_type" ]; then
            if [ "$(cat "$BACKUP_DIR/session_type")" == "graphical" ]; then
                echo "Переключаю в графический режим"
                sudo systemctl isolate graphical.target
            fi
        fi
    fi
fi

echo "=================================="
echo "6. Информация о восстановлении"
echo "=================================="

echo "✅ Восстановление из бэкапа $SESSION_ID завершено!"
echo ""
echo "Проверьте важные сервисы:"
echo "- systemctl status NetworkManager"
echo "- systemctl status snapd"
echo "- systemctl status power-profiles-daemon"
echo ""
echo "Рекомендуется перезагрузить систему для полного восстановления"
echo "sudo reboot"
echo ""
echo "Бэкап сохранен для истории: $BACKUP_DIR"
echo "Для удаления всех старых бэкапов выполните:"
echo "rm -rf /tmp/benchmark_backup_*"
