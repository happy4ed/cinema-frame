package com.happy.cinemaframe;
import java.lang.reflect.*;
import org.json.*;
import sun.misc.Unsafe;
/** MOCK metadata: executes the production eligibility filter. */
public class CountryContractRegression {
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
  FrameCore core=(FrameCore)((Unsafe)f.get(null)).allocateInstance(FrameCore.class);
  Method eligible=FrameCore.class.getDeclaredMethod("eligible",JSONObject.class,JSONObject.class);eligible.setAccessible(true);
  JSONObject m=new JSONObject().put("id","fixture").put("year",1985).put("rating",8).put("votes",100000).put("genres",new JSONArray().put("Sci-Fi"));
  JSONObject p=new JSONObject().put("excluded_countries",new JSONArray().put("CN"));
  m.put("countries",new JSONArray().put("CN").put("HK"));
  if((Boolean)eligible.invoke(core,m,p))throw new AssertionError("CN co-production must be excluded");
  m.put("countries",new JSONArray().put("HK"));
  if(!(Boolean)eligible.invoke(core,m,p))throw new AssertionError("HK must not be conflated with CN");
  p.put("excluded_countries",new JSONArray().put("HK"));
  if((Boolean)eligible.invoke(core,m,p))throw new AssertionError("HK exclusion missing");
  m.put("countries",new JSONArray());
  if(!(Boolean)eligible.invoke(core,m,p))throw new AssertionError("Unknown country must remain eligible");
  System.out.println("PASS CountryContractRegression (MOCK): co-production, CN/HK separation, unknown retained");
 }
}
