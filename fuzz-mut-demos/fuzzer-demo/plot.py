import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import csv
import sys
import os


def plot_coverage(csv_file, output_image):
    times = []
    coverage = []

    # 1. 读取 CSV 数据
    try:
        with open(csv_file, 'r') as f:
            reader = csv.reader(f)
            next(reader)  # 跳过表头
            for row in reader:
                if len(row) < 3: continue
                times.append(float(row[0]))  # Time
                # row[1] 是 execs，这里我们要画 覆盖率(row[2])
                coverage.append(int(row[2]))  # Coverage
    except FileNotFoundError:
        print(f"Error: Log file not found at {csv_file}")
        return

    if not times:
        print("No data to plot.")
        return

    # 2. 设置画布风格
    plt.figure(figsize=(10, 6))
    plt.plot(times, coverage, label='Edge Coverage', color='b', linewidth=2)

    # 3. 添加标签和标题
    plt.title('Fuzzing Coverage Trend', fontsize=16)
    plt.xlabel('Time (seconds)', fontsize=12)
    plt.ylabel('Paths Found (Bitmap Entries)', fontsize=12)
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.legend()

    # 4. 保存图片
    plt.savefig(output_image)
    print(f"[+] Plot saved to: {output_image}")


if __name__ == "__main__":
    # 默认路径配置
    # 假设 Java 会把 CSV 存在 'output/fuzzer_stats.csv'
    csv_path = 'output/fuzzer_stats.csv'
    img_path = 'output/coverage_trend.png'

    # 支持命令行参数覆盖
    if len(sys.argv) > 1:
        csv_path = sys.argv[1]
    if len(sys.argv) > 2:
        img_path = sys.argv[2]

    if os.path.exists(csv_path):
        plot_coverage(csv_path, img_path)
    else:
        print(f"Waiting for data... ({csv_path} not found yet)")