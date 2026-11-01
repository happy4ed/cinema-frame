package com.happy.cinemaframe;
public class AudioLevelerRegression {
 static double tone(double amplitude)throws Exception{
  AudioLeveler leveler=new AudioLeveler();double sum=0;long samples=0;int frame=0;
  for(int block=0;block<520;block++){
   byte[] pcm=new byte[4096];for(int i=0;i<pcm.length;i+=4,frame++){int v=(int)(Math.sin(2*Math.PI*440*frame/44100)*32767*amplitude);pcm[i]=pcm[i+2]=(byte)v;pcm[i+1]=pcm[i+3]=(byte)(v>>8);}
   leveler.process(pcm,pcm.length);
   for(int i=0;i<pcm.length;i+=4){int l=(short)((pcm[i]&255)|(pcm[i+1]<<8)),r=(short)((pcm[i+2]&255)|(pcm[i+3]<<8));if(l!=r||Math.abs(l)>29191)throw new AssertionError("Stereo/peak guard");if(block>430){sum+=(double)l*l;samples++;}}
  }
  return Math.sqrt(sum/samples)/32768;
 }
 public static void main(String[] args)throws Exception{
  double quiet=tone(.04),loud=tone(.8);if(Math.abs(20*Math.log10(loud/quiet))>2)throw new AssertionError("Unequal output: "+quiet+", "+loud);
  AudioLeveler leveler=new AudioLeveler();byte[] silence=new byte[8192];leveler.process(silence,silence.length);for(byte b:silence)if(b!=0)throw new AssertionError("Silence changed");
  System.out.println("PASS audio leveling: input difference 26 dB; output difference "+Math.round(100*20*Math.log10(loud/quiet))/100.0+" dB, stereo preserved, peaks bounded, silence preserved");
 }
}
