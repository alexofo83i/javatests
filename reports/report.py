#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
JMH Multi-threaded Benchmark Visualizer
----------------------------------------
Генерирует графики и отчеты из JSON-файла результатов JMH для многопоточных
бенчмарков org.fedorov.uniq.lists.MultiThreadUniqueListBenchmark.

Сравнивает производительность (Throughput) различных реализаций IUniqueList
в зависимости от количества потоков (2, 4, 8, 16).
"""

import json
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import sys
import argparse
from datetime import datetime
from matplotlib.backends.backend_pdf import PdfPages
import traceback

# --- Конфигурация ---
# Список реализаций в том порядке, в котором они должны отображаться в легенде и таблицах
# (берется из params.implementationName в JSON)
IMPLEMENTATION_NAMES = [
    "SYNCHRONIZED_METHOD",
    "SYNCHRONIZED_SECTION",
    "ATOMIC_BOOLEAN",
    "VALID_VOLATILE",
    "REENTRANT_LOCK"
]

# Человеко-понятные имена для легенды (можно настроить)
DISPLAY_NAMES = {
    "SYNCHRONIZED_METHOD": "Synchronized Method",
    "SYNCHRONIZED_SECTION": "Synchronized Section",
    "ATOMIC_BOOLEAN": "AtomicBoolean Lock",
    "VALID_VOLATILE": "Valid Volatile Lock",
    "REENTRANT_LOCK": "ReentrantLock"
}

# Параметры тестирования (из benchmark)
THREAD_CONFIGS = [2, 4, 8, 16]
OPERATIONS_PER_THREAD = 10  # Из параметров benchmark

# Палитра цветов для графиков (один цвет на реализацию)
COLOR_PALETTE = [
    '#1f77b4',  # blue
    '#ff7f0e',  # orange
    '#2ca02c',  # green
    '#d62728',  # red
    '#9467bd',  # purple
    '#8c564b',  # brown
    '#e377c2',  # pink
    '#7f7f7f',  # gray
    '#bcbd22',  # olive
    '#17becf'   # cyan
]

# Маркеры для точек на графике
MARKERS = ['o', 's', '^', 'D', 'v', '<', '>', 'p', '*', 'h']

# --- Функции для получения цвета и маркера ---
def get_color_for_impl(idx):
    """Возвращает цвет для индекса реализации (с циклическим перебором)"""
    return COLOR_PALETTE[idx % len(COLOR_PALETTE)]

def get_marker_for_impl(idx):
    """Возвращает маркер для индекса реализации (с циклическим перебором)"""
    return MARKERS[idx % len(MARKERS)]

def get_display_name(impl_name):
    """Возвращает отображаемое имя для реализации"""
    return DISPLAY_NAMES.get(impl_name, impl_name)

# --- Функции для парсинга и подготовки данных ---
def load_and_prepare_data(json_file_path):
    """
    Загружает JMH JSON и преобразует в DataFrame для анализа.
    """
    print(f"Загрузка данных из: {json_file_path}")
    with open(json_file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    records = []
    for benchmark_result in data:
        params = benchmark_result.get('params', {})
        benchmark_name = benchmark_result.get('benchmark', '')
        mode = benchmark_result.get('mode', '')
        
        # Нас интересуют только результаты Throughput (thrpt)
        if mode != 'thrpt':
            continue
            
        # Извлекаем количество потоков из имени benchmark или из поля 'threads'
        threads = benchmark_result.get('threads', 0)
        
        impl_name = params.get('implementationName')
        ops_per_thread = int(params.get('operationsPerThread', 10))
        
        primary_metric = benchmark_result.get('primaryMetric', {})
        score = primary_metric.get('score', np.nan)  # ops/us
        score_error = primary_metric.get('scoreError', np.nan)
        
        # Преобразуем в операции в секунду для лучшей читаемости (умножаем на 1e6)
        score_ops_per_sec = score * 1e6 if not np.isnan(score) else np.nan
        score_error_ops_per_sec = score_error * 1e6 if not np.isnan(score_error) else np.nan
        
        records.append({
            'implementation': impl_name,
            'display_name': get_display_name(impl_name),
            'threads': threads,
            'ops_per_thread': ops_per_thread,
            'score_ops_per_us': score,
            'score_error_ops_per_us': score_error,
            'score_ops_per_sec': score_ops_per_sec,
            'score_error_ops_per_sec': score_error_ops_per_sec,
            # Сохраняем "сырые" данные на всякий случай
            'raw_data': primary_metric.get('rawData', [[]])[0]
        })

    if not records:
        raise ValueError("Не найдено записей с режимом 'thrpt' в JSON файле.")

    df = pd.DataFrame(records)
    
    # Фильтруем только нужные конфигурации потоков и убеждаемся, что реализация в нашем списке
    df = df[df['threads'].isin(THREAD_CONFIGS)]
    df = df[df['implementation'].isin(IMPLEMENTATION_NAMES)]
    
    # Сортируем для консистентности
    df = df.sort_values(by=['implementation', 'threads']).reset_index(drop=True)
    
    print(f"Загружено {len(df)} записей для {df['implementation'].nunique()} реализаций.")
    return df

def calculate_throughput_per_operation(df):
    """
    Рассчитывает пропускную способность на одну операцию.
    Общая пропускная способность (ops/sec) = score_ops_per_sec.
    Это уже нормализовано JMH. Можно добавить другие метрики при необходимости.
    """
    # В данном случае, score_ops_per_sec уже является итоговой метрикой.
    # Добавим колонку для удобства.
    df['throughput_ops_per_sec'] = df['score_ops_per_sec']
    df['throughput_error_ops_per_sec'] = df['score_error_ops_per_sec']
    return df

# --- Функции для визуализации ---
def plot_throughput_comparison(df, output_dir, show_plots=False):
    """
    Создает основной график: Throughput (ops/sec) vs. Threads для всех реализаций.
    """
    print("Построение графика Throughput vs. Threads...")
    
    unique_impls = df['implementation'].unique()
    
    fig, ax = plt.subplots(figsize=(12, 8))
    
    for idx, impl in enumerate(unique_impls):
        impl_data = df[df['implementation'] == impl].sort_values('threads')
        display_name = get_display_name(impl)
        color = get_color_for_impl(idx)
        marker = get_marker_for_impl(idx)
        
        x = impl_data['threads'].values
        y = impl_data['throughput_ops_per_sec'].values
        yerr = impl_data['throughput_error_ops_per_sec'].values
        
        # Основная линия и точки
        ax.plot(x, y, color=color, marker=marker, markersize=8, 
                linewidth=2, label=display_name, zorder=3)
        
        # Полосы ошибок (стандартное отклонение/доверительный интервал)
        ax.fill_between(x, y - yerr, y + yerr, color=color, alpha=0.15, zorder=2)
        # Или использовать errorbar:
        # ax.errorbar(x, y, yerr=yerr, color=color, marker=marker, capsize=3, linestyle='-', linewidth=2)
    
    # Настройка осей и заголовка
    ax.set_xlabel('Number of Threads', fontsize=14)
    ax.set_ylabel('Throughput (operations / second)', fontsize=14)
    ax.set_title('JMH Multi-threaded Benchmark: Throughput vs. Threads\n(Higher is Better)', fontsize=16, fontweight='bold')
    
    ax.set_xscale('log', base=2)  # Логарифмическая шкала для потоков (2,4,8,16)
    ax.set_xticks(THREAD_CONFIGS)
    ax.set_xticklabels([str(t) for t in THREAD_CONFIGS])
    
    ax.grid(True, which='both', linestyle='--', alpha=0.6, zorder=1)
    ax.legend(fontsize=12, loc='best')
    
    plt.tight_layout()
    
    # Сохраняем график
    plot_filename = os.path.join(output_dir, 'throughput_vs_threads.png')
    plt.savefig(plot_filename, dpi=150, bbox_inches='tight')
    print(f"График сохранен: {plot_filename}")
    
    if show_plots:
        plt.show()
    else:
        plt.close(fig)
    
    return fig

def plot_individual_impl(df, output_dir, show_plots=False):
    """
    Создает отдельные графики для каждой реализации.
    """
    print("Построение индивидуальных графиков...")
    figs = []
    unique_impls = df['implementation'].unique()
    
    for impl in unique_impls:
        impl_data = df[df['implementation'] == impl].sort_values('threads')
        display_name = get_display_name(impl)
        
        fig, ax = plt.subplots(figsize=(10, 6))
        
        x = impl_data['threads'].values
        y = impl_data['throughput_ops_per_sec'].values
        yerr = impl_data['throughput_error_ops_per_sec'].values
        
        ax.plot(x, y, 'o-', color='#1f77b4', markersize=8, linewidth=2, label=display_name)
        ax.fill_between(x, y - yerr, y + yerr, color='#1f77b4', alpha=0.2)
        
        ax.set_xlabel('Number of Threads', fontsize=12)
        ax.set_ylabel('Throughput (operations / second)', fontsize=12)
        ax.set_title(f'Performance: {display_name}', fontsize=14, fontweight='bold')
        ax.set_xscale('log', base=2)
        ax.set_xticks(THREAD_CONFIGS)
        ax.set_xticklabels([str(t) for t in THREAD_CONFIGS])
        ax.grid(True, linestyle='--', alpha=0.6)
        
        plt.tight_layout()
        
        # Сохраняем
        safe_name = impl.lower().replace('_', '-')
        plot_filename = os.path.join(output_dir, f'throughput_{safe_name}.png')
        plt.savefig(plot_filename, dpi=150, bbox_inches='tight')
        
        if show_plots:
            plt.show()
        else:
            plt.close(fig)
        
        figs.append(fig)
    
    print(f"Сохранено {len(figs)} индивидуальных графиков.")
    return figs

# --- Функции для отчетов ---
def create_pdf_report(df, all_figures, output_dir, input_filename):
    """
    Создает PDF отчет, объединяя все графики и сводные таблицы.
    """
    pdf_filename = os.path.join(output_dir, f'{input_filename}_report.pdf')
    print(f"Создание PDF отчета: {pdf_filename}")
    
    with PdfPages(pdf_filename) as pdf:
        # Титульная страница
        fig, ax = plt.subplots(figsize=(8.5, 11))
        ax.axis('off')
        
        title_text = (
            f"JMH MULTITHREADED BENCHMARK REPORT\n"
            f"====================================\n\n"
            f"Source File: {input_filename}\n"
            f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
            f"Benchmark: MultiThreadUniqueListBenchmark\n"
            f"Operations per Thread: {OPERATIONS_PER_THREAD}\n"
            f"Thread Configs: {THREAD_CONFIGS}\n\n"
            f"Implementations Compared:\n"
        )
        for impl in IMPLEMENTATION_NAMES:
            title_text += f"  • {get_display_name(impl)}\n"
        
        ax.text(0.5, 0.5, title_text, transform=ax.transAxes, fontsize=14,
                ha='center', va='center', linespacing=1.5,
                bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
        pdf.savefig(fig, bbox_inches='tight')
        plt.close(fig)
        
        # Добавляем основные графики
        print("Добавление графиков в PDF...")
        for i, fig in enumerate(all_figures):
            pdf.savefig(fig, bbox_inches='tight')
            plt.close(fig)  # Закрываем, чтобы освободить память
        
        # Страница со сводной таблицей результатов
        fig, ax = plt.subplots(figsize=(8.5, 11))
        ax.axis('off')
        
        # Создаем сводную таблицу
        pivot_df = df.pivot_table(
            index='implementation', 
            columns='threads', 
            values='throughput_ops_per_sec',
            aggfunc='first'
        ).reindex(IMPLEMENTATION_NAMES)
        
        # Форматируем значения (в миллионы операций в секунду для читаемости)
        formatted_data = []
        for impl in pivot_df.index:
            row = [get_display_name(impl)]
            for t in THREAD_CONFIGS:
                val = pivot_df.loc[impl, t] if t in pivot_df.columns else np.nan
                if not np.isnan(val):
                    row.append(f"{val/1e6:.2f} M ops/sec")
                else:
                    row.append("N/A")
            formatted_data.append(row)
        
        # Создаем таблицу matplotlib
        columns = ['Implementation'] + [f'{t} Threads' for t in THREAD_CONFIGS]
        table = ax.table(cellText=formatted_data, colLabels=columns,
                         loc='center', cellLoc='center', colWidths=[0.25]*len(columns))
        
        table.auto_set_font_size(False)
        table.set_fontsize(10)
        table.scale(1, 2)
        
        # Раскраска заголовка
        for (i, j), cell in table.get_celld().items():
            if i == 0:  # Заголовок
                cell.set_facecolor('#40466e')
                cell.set_text_props(weight='bold', color='white')
            elif j == 0:  # Первая колонка (названия реализаций)
                cell.set_facecolor('#e0e0e0')
                cell.set_text_props(weight='bold')
        
        ax.set_title('Summary Table: Throughput (M operations/sec)', fontsize=16, weight='bold', y=0.95)
        pdf.savefig(fig, bbox_inches='tight')
        plt.close(fig)
        
        # Страница с лучшими результатами
        fig, ax = plt.subplots(figsize=(8.5, 11))
        ax.axis('off')
        
        best_results_text = "BEST PERFORMANCE SUMMARY\n"
        best_results_text += "========================\n\n"
        
        for t in THREAD_CONFIGS:
            best_for_threads = df[df['threads'] == t].sort_values('throughput_ops_per_sec', ascending=False)
            if not best_for_threads.empty:
                best_impl = best_for_threads.iloc[0]
                best_results_text += f"Threads = {t}:\n"
                best_results_text += f"  • Winner: {get_display_name(best_impl['implementation'])}\n"
                best_results_text += f"  • Throughput: {best_impl['throughput_ops_per_sec']/1e6:.2f} M ops/sec\n"
                best_results_text += f"  • Error: ±{best_impl['throughput_error_ops_per_sec']/1e6:.2f} M ops/sec\n\n"
        
        ax.text(0.1, 0.9, best_results_text, transform=ax.transAxes, fontsize=12,
                va='top', linespacing=1.5, family='monospace')
        pdf.savefig(fig, bbox_inches='tight')
        plt.close(fig)
    
    print(f"PDF отчет успешно создан: {pdf_filename}")
    return pdf_filename

def save_summary_csv(df, output_dir, input_filename):
    """
    Сохраняет сводные данные в CSV.
    """
    summary_path = os.path.join(output_dir, f'{input_filename}_summary.csv')
    
    summary_df = df[['implementation', 'display_name', 'threads', 
                     'throughput_ops_per_sec', 'throughput_error_ops_per_sec']].copy()
    summary_df['throughput_ops_per_sec_millions'] = summary_df['throughput_ops_per_sec'] / 1e6
    summary_df['throughput_error_ops_per_sec_millions'] = summary_df['throughput_error_ops_per_sec'] / 1e6
    
    summary_df.to_csv(summary_path, index=False, float_format='%.4f')
    print(f"CSV сводка сохранена: {summary_path}")
    
    # Сохраняем также в удобном для чтения формате (pivot table)
    pivot_path = os.path.join(output_dir, f'{input_filename}_pivot.csv')
    pivot_df = df.pivot_table(
        index='implementation', 
        columns='threads', 
        values='throughput_ops_per_sec',
        aggfunc='first'
    ).reindex(IMPLEMENTATION_NAMES)
    pivot_df.to_csv(pivot_path, float_format='%.2f')
    print(f"Pivot-таблица сохранена: {pivot_path}")
    
    return summary_path

# --- Основная функция ---
def main():
    parser = argparse.ArgumentParser(description='JMH Multithreaded Benchmark Visualizer')
    parser.add_argument('--input', '-i', type=str, required=True,
                        help='Path to input JMH JSON results file')
    parser.add_argument('--output-dir', '-o', type=str, default=None,
                        help='Output directory for reports (default: same as input file)')
    parser.add_argument('--show-plots', action='store_true', default=False,
                        help='Show plots on screen (default: False)')
    parser.add_argument('--no-pdf', dest='create_pdf', action='store_false', default=True,
                        help='Do not generate PDF report')
    
    args = parser.parse_args()
    
    input_file = args.input
    if not os.path.exists(input_file):
        input_file = "../results/multithread-results.json"
        # print(f"Ошибка: Файл не найден: {input_file}")
        # sys.exit(1)
    
    # Определяем выходную директорию
    if args.output_dir:
        output_dir = os.path.abspath(args.output_dir)
    else:
        output_dir = os.path.dirname(os.path.abspath(input_file))
    
    os.makedirs(output_dir, exist_ok=True)
    
    # Базовое имя входного файла без расширения
    input_filename = os.path.splitext(os.path.basename(input_file))[0]
    
    try:
        # 1. Загрузка и подготовка данных
        df = load_and_prepare_data(input_file)
        df = calculate_throughput_per_operation(df)
        
        # 2. Сохраняем сырые данные для отладки (опционально)
        debug_path = os.path.join(output_dir, f'{input_filename}_processed.csv')
        df.to_csv(debug_path, index=False)
        print(f"Обработанные данные сохранены: {debug_path}")
        
        # 3. Визуализация
        all_figures = []
        
        # Основной график
        fig_main = plot_throughput_comparison(df, output_dir, args.show_plots)
        all_figures.append(fig_main)
        
        # Индивидуальные графики
        individual_figs = plot_individual_impl(df, output_dir, args.show_plots)
        all_figures.extend(individual_figs)
        
        # 4. Сохранение сводок
        save_summary_csv(df, output_dir, input_filename)
        
        # 5. PDF отчет
        if args.create_pdf:
            create_pdf_report(df, all_figures, output_dir, input_filename)
        else:
            print("PDF отчет пропущен по запросу пользователя.")
        
        print(f"\nВсе файлы сохранены в директории: {output_dir}")
        print("Анализ завершен успешно!")
        
    except Exception as e:
        print(f"Ошибка при обработке: {e}")
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()