package com.happy.cinemaframe;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;
/** Queue scheduling, independent of Android and network state. */
final class PlaybackPlan {
 static <T> int next(List<T> plan,int cursor,Predicate<T> allowed,Function<T,String> group){
  Map<String,Integer> counts=new HashMap<>();
  for(int i=cursor;i<plan.size();i++)if(allowed.test(plan.get(i)))counts.merge(group.apply(plan.get(i)),1,Integer::sum);
  int selected=-1,largest=0;
  for(int i=cursor;i<plan.size();i++)if(allowed.test(plan.get(i))){int n=counts.get(group.apply(plan.get(i)));if(n>largest){selected=i;largest=n;}}
  if(selected<0)return -1;
  Collections.swap(plan,cursor,selected);return cursor;
 }
 static boolean expand(boolean explicitCache,boolean cached,long retryAt,long now){
  return !explicitCache && cached && retryAt<=now;
 }
}
