package com.sma.admin.monitor.internal.service;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

@Service
public class ServerService {
 private final DataSource dataSource;public ServerService(DataSource dataSource){this.dataSource=dataSource;}
 public record Disk(String name,String type,long totalBytes,long usableBytes){}
 public record Pool(int active,int idle,int total,int waiting){}
 public record View(Instant sampledAt,String os,String arch,int processors,Double cpuLoad,Double processCpuLoad,long physicalTotalBytes,long physicalFreeBytes,
  String javaVersion,String vm,long heapUsedBytes,long heapCommittedBytes,long heapMaxBytes,long nonHeapUsedBytes,int threads,long uptimeMillis,List<Disk> disks,Pool pool){}
 public View get(){
  var os=ManagementFactory.getOperatingSystemMXBean();var memory=ManagementFactory.getMemoryMXBean();var runtime=ManagementFactory.getRuntimeMXBean();
  Double cpu=null,process=null;long total=0,free=0;
  if(os instanceof com.sun.management.OperatingSystemMXBean extended){cpu=load(extended.getCpuLoad());process=load(extended.getProcessCpuLoad());total=extended.getTotalMemorySize();free=extended.getFreeMemorySize();}
  List<Disk> disks=new ArrayList<>();
  for(Path root:FileSystems.getDefault().getRootDirectories())try{var disk=Files.getFileStore(root);disks.add(new Disk(root.toString(),disk.type(),disk.getTotalSpace(),disk.getUsableSpace()));}catch(IOException|SecurityException ignored){/* Offline/missing drives are unavailable samples. */}
  Pool pool=null;try{var hikari=dataSource.unwrap(HikariDataSource.class);var stats=hikari.getHikariPoolMXBean();if(stats!=null)pool=new Pool(stats.getActiveConnections(),stats.getIdleConnections(),stats.getTotalConnections(),stats.getThreadsAwaitingConnection());}catch(java.sql.SQLException ignored){/* Other DataSource implementations can omit pool metrics. */}
  var heap=memory.getHeapMemoryUsage();return new View(Instant.now(),os.getName(),os.getArch(),os.getAvailableProcessors(),cpu,process,total,free,
   System.getProperty("java.version"),System.getProperty("java.vm.name"),heap.getUsed(),heap.getCommitted(),heap.getMax(),memory.getNonHeapMemoryUsage().getUsed(),ManagementFactory.getThreadMXBean().getThreadCount(),runtime.getUptime(),List.copyOf(disks),pool);
 }
 private Double load(double value){return value<0||!Double.isFinite(value)?null:Math.min(1,value);}
}
