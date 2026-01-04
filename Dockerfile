# 1. 选择基础镜像 (使用 Ubuntu 22.04，比较新且稳定)
FROM ubuntu:22.04

# 设置环境变量，防止安装过程中出现交互式弹窗
ENV DEBIAN_FRONTEND=noninteractive

# 2. 换源 (可选，为了下载更快，这里使用阿里云源，如果朋友在海外可去掉这一块)
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list

# 3. 安装基础环境 (Java, Maven, Python, 常用工具)
# 我们假设你的 Fuzzer 需要 Java 8 或 11，这里安装 openjdk-11-jdk
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    maven \
    python3 \
    python3-pip \
    vim \
    git \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# 4. 安装 Python 画图库 (给 plot.py 用)
RUN pip3 install matplotlib

# 5. [关键] 安装目标程序所需的依赖库 (复刻你之前的修复操作)
# zlib (for readelf), libpcap (for tcpdump), libjpeg/png (for images), libxml2
RUN apt-get update && apt-get install -y \
    zlib1g zlib1g-dev \
    libpcap-dev \
    libjpeg-dev \
    libpng-dev \
    libxml2-dev \
    make gcc \
    && rm -rf /var/lib/apt/lists/*

# 6. 设置工作目录
WORKDIR /app

# 7. 默认命令 (进入容器时默认打开 bash)
CMD ["/bin/bash"]