package com.sma.admin.common.security;
import java.time.Instant;
import java.util.List;
/** Each module queries its own data and contributes only information visible to the current human user. */
public interface DashboardContributor {
 record Metric(String id,String nameKey,long value,String path){}
 record Entry(String kindKey,String title,String path,Instant time){}
 record Contribution(List<Metric> metrics,List<Entry> entries){public Contribution{metrics=List.copyOf(metrics);entries=List.copyOf(entries);}}
 Contribution contribute();
}
