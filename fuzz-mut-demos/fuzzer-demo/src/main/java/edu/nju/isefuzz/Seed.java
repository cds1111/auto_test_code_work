package edu.nju.isefuzz;

import java.io.File;
import java.nio.file.Files;

public class Seed {
    private String filename;
    private byte[] data;
    private long length;

    // [新增] 执行指标
    private long execTime;     // 执行耗时 (ms)
    private int bitmapSize;    // 覆盖了多少条边 (Covered Edges Count)

    public Seed(File file) throws Exception {
        this.filename = file.getAbsolutePath();
        this.data = Files.readAllBytes(file.toPath());
        this.length = this.data.length;
    }

    public Seed(String filename, byte[] data) {
        this.filename = filename;
        this.data = data;
        this.length = data.length;
    }

    // Getters & Setters
    public String getFilename() { return filename; }
    public byte[] getData() { return data; }
    public long getLength() { return length; }

    public long getExecTime() { return execTime; }
    public void setExecTime(long execTime) { this.execTime = execTime; }

    public int getBitmapSize() { return bitmapSize; }
    public void setBitmapSize(int bitmapSize) { this.bitmapSize = bitmapSize; }

    @Override
    public String toString() {
        return "Seed{name='" + new File(filename).getName() + "', len=" + length +
                ", time=" + execTime + ", cov=" + bitmapSize + "}";
    }
}
//package edu.nju.isefuzz;
//
//import java.io.File;
//import java.nio.file.Files;
//
//public class Seed {
//    private String filename;
//    private byte[] data;
//    private long length;
//
//    // [新增] 执行指标
//    private long execTime;     // 执行耗时 (ms)
//    private int bitmapSize;    // 覆盖了多少条边 (Covered Edges Count)
//
//    public Seed(File file) throws Exception {
//        this.filename = file.getAbsolutePath();
//        this.data = Files.readAllBytes(file.toPath());
//        this.length = this.data.length;
//    }
//
//    public Seed(String filename, byte[] data) {
//        this.filename = filename;
//        this.data = data;
//        this.length = data.length;
//    }
//
//    // Getters & Setters
//    public String getFilename() { return filename; }
//    public byte[] getData() { return data; }
//    public long getLength() { return length; }
//
//    public long getExecTime() { return execTime; }
//    public void setExecTime(long execTime) { this.execTime = execTime; }
//
//    public int getBitmapSize() { return bitmapSize; }
//    public void setBitmapSize(int bitmapSize) { this.bitmapSize = bitmapSize; }
//
//    @Override
//    public String toString() {
//        return "Seed{name='" + new File(filename).getName() + "', len=" + length +
//                ", time=" + execTime + ", cov=" + bitmapSize + "}";
//    }
//}