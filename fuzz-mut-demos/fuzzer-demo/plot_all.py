import matplotlib

matplotlib.use('Agg')  # 不弹窗模式
import matplotlib.pyplot as plt
import csv
import os
import sys


def plot_all_targets(output_base_dir, save_path):
    # 定义我们要找的目标列表
    targets = ["T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08", "T09", "T10"]

    # --- [新增] 目标 ID 与名称的映射表 ---
    target_names = {
        "T01": "cxxfilt",
        "T02": "readelf",
        "T03": "nm-new",
        "T04": "objdump",
        "T05": "djpeg",
        "T06": "readpng",
        "T07": "xmllint",
        "T08": "mjs",
        "T09": "lua",
        "T10": "tcpdump"
    }

    # 设置画布大小
    plt.figure(figsize=(12, 8))

    has_data = False

    # 颜色映射
    colors = ['b', 'g', 'r', 'c', 'm', 'y', 'k', 'orange', 'purple', 'brown']

    for i, target_id in enumerate(targets):
        # 构造 CSV 路径
        csv_file = os.path.join(output_base_dir, target_id, "fuzzer_stats.csv")

        times = []
        coverage = []

        if not os.path.exists(csv_file):
            print(f"[Warn] No data for {target_id} (file not found)")
            continue

        try:
            with open(csv_file, 'r') as f:
                reader = csv.reader(f)
                header = next(reader, None)  # 跳过表头
                if not header: continue

                for row in reader:
                    if len(row) < 3: continue
                    t = float(row[0])
                    cov = int(row[2])

                    if t > 0:
                        times.append(t)
                        coverage.append(cov)

            if times:
                has_data = True

                # --- [修改核心] 生成带名称的标签 ---
                # 从字典里取名字，如果没取到就默认 Unknown
                name = target_names.get(target_id, "Unknown")
                full_label = f"{target_id}_{name}"  # 例如: T01_cxxfilt

                # 画线
                plt.plot(times, coverage, label=full_label, color=colors[i % len(colors)], linewidth=2, alpha=0.8)
                print(f"[Info] Loaded {full_label}: {len(times)} points, Max Cov: {max(coverage)}")
            else:
                print(f"[Warn] {target_id} has empty data.")

        except Exception as e:
            print(f"[Error] Failed to read {target_id}: {e}")

    if not has_data:
        print("[Error] No valid data found for any target!")
        return

    # 设置图表样式
    plt.title('Fuzzing Coverage Trend (All Targets)', fontsize=18)
    plt.xlabel('Time (seconds)', fontsize=14)
    plt.ylabel('Paths Found (Bitmap Entries)', fontsize=14)
    plt.grid(True, linestyle='--', alpha=0.5)

    # 图例放在右下角，字体稍微小一点防止遮挡
    plt.legend(loc='lower right', fontsize=10)

    # 保存
    plt.savefig(save_path)
    print(f"\n[Success] Combined plot saved to: {save_path}")


if __name__ == "__main__":
    # 默认路径配置
    base_dir = "output"
    target_img = "output/combined_trend.png"

    if len(sys.argv) > 1:
        base_dir = sys.argv[1]

    plot_all_targets(base_dir, target_img)