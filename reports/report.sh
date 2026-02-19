#!/bin/bash

# run_report_full.sh - полный скрипт с созданием структуры директорий

#set -e  # Прерывать выполнение при ошибке

echo "==================================="
echo "Запуск генерации отчета"
echo "==================================="

# Параметры
INPUT_FILE="${1:-multithread-results.json}"
REPORT_SCRIPT="${2:-report.py}"

# Создаем необходимые директории
mkdir -p results
mkdir -p reports

# Проверяем наличие входного файла
if [ ! -f "$INPUT_FILE" ]; then
    echo "Ошибка: Файл $INPUT_FILE не найден!"
    echo "Использование: $0 [input_file] [report_script]"
    exit 1
fi

# Настройка виртуального окружения
if [ ! -d "venv" ]; then
    echo "→ Создание виртуального окружения..."
    python3 -m venv venv
fi

echo "→ Активация виртуального окружения..."
source venv/bin/activate

# Установка зависимостей
echo "→ Установка/обновление зависимостей..."
pip install --upgrade pip
pip install pandas matplotlib numpy

# Копируем входной файл в results если нужно
if [ ! -f "results/$INPUT_FILE" ] && [ "$INPUT_FILE" != "results/"* ]; then
    echo "→ Копирование входного файла в директорию results..."
    cp "$INPUT_FILE" results/
    INPUT_FILE="results/$INPUT_FILE"
fi

# Запуск скрипта
echo "→ Запуск $REPORT_SCRIPT..."
python3 "$REPORT_SCRIPT" --input "$INPUT_FILE" --output-dir ./reports

echo "→ Деактивация виртуального окружения..."
deactivate

echo "==================================="
echo "Готово! Результаты в директории ./reports"
echo "==================================="
