package com.sma.admin.workspace.internal.service;
import java.time.Instant;
import java.util.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import com.sma.admin.common.security.*;
@Service
public class DashboardService {
 private final List<DashboardContributor> contributors;private final MessageSource messages;
 public DashboardService(List<DashboardContributor> contributors,MessageSource messages){this.contributors=contributors;this.messages=messages;}
 public record Metric(String id,String name,long value,String path){}
 public record Entry(String kind,String title,String path,Instant time){}
 public record View(List<Metric> metrics,List<Entry> entries){}
 public View get(){CurrentUser.require();List<Metric> metrics=new ArrayList<>();List<Entry> entries=new ArrayList<>();Set<String> keys=new HashSet<>();
  for(var contributor:contributors){var data=contributor.contribute();for(var row:data.metrics()){if(!keys.add(row.id()))throw new IllegalStateException("Duplicate dashboard key: "+row.id());validPath(row.path());metrics.add(new Metric(row.id(),text(row.nameKey()),row.value(),row.path()));}
   for(var row:data.entries()){validPath(row.path());entries.add(new Entry(text(row.kindKey()),row.title(),row.path(),row.time()));}}
  metrics.sort(Comparator.comparing(Metric::id));return new View(List.copyOf(metrics),entries.stream().limit(10).toList());
 }
 private String text(String key){return messages.getMessage(key,null,key,LocaleContextHolder.getLocale());}
 private void validPath(String path){if(path==null||!path.startsWith("/")||path.startsWith("//")||path.indexOf('\\')>=0)throw new IllegalStateException("Dashboard links must be local");}
}
