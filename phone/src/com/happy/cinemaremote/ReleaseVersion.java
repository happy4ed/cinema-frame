package com.happy.cinemaremote;
final class ReleaseVersion {
 static boolean newer(String tag,String installed){
  int[] a=parse(tag),b=parse(installed);for(int i=0;i<3;i++){if(a[i]!=b[i])return a[i]>b[i];}return false;
 }
 static int[] parse(String text){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^v?([0-9]+)\\.([0-9]+)(?:\\.([0-9]+))?(?:[-+].*)?$").matcher(text);if(!m.matches())throw new IllegalArgumentException("지원하지 않는 릴리스 버전");return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),m.group(3)==null?0:Integer.parseInt(m.group(3))};}
}
