package com.happy.cinemaframe;

/** Stereo-linked, bounded RMS level control and peak limiting for 44.1 kHz PCM. */
final class AudioLeveler {
 private double energy=0, gain=1, limiter=1;
 private static final double ENERGY=1-Math.exp(-1.0/(44100*.2));
 private static final double ATTACK=1-Math.exp(-1.0/(44100*.08));
 private static final double RELEASE=1-Math.exp(-1.0/(44100*2));
 private static final double LIMIT_RELEASE=1-Math.exp(-1.0/(44100*.5));
 void reset(){energy=0;gain=limiter=1;}
 void process(byte[] pcm,int length){
  for(int i=0;i+3<length;i+=4){
   int l=(short)((pcm[i]&255)|(pcm[i+1]<<8)),r=(short)((pcm[i+2]&255)|(pcm[i+3]<<8));
   double power=((double)l*l+(double)r*r)/(2*32768.0*32768.0);
   energy+=ENERGY*(power-energy);
   double rms=Math.sqrt(energy);
   // Do not amplify silence/very quiet tails. Bound amplification to +12 dB.
   double desired=rms<.003?Math.min(1,gain):Math.max(.125,Math.min(4,.1/rms));
   gain+=(desired<gain?ATTACK:RELEASE)*(desired-gain);
   double peak=Math.max(Math.abs((double)l),Math.abs((double)r))*gain;
   double limit=peak>29190?29190/peak:1;
   if(limit<limiter)limiter=limit;else limiter+=LIMIT_RELEASE*(limit-limiter);
   int left=(int)Math.round(l*gain*limiter),right=(int)Math.round(r*gain*limiter);
   pcm[i]=(byte)left;pcm[i+1]=(byte)(left>>8);pcm[i+2]=(byte)right;pcm[i+3]=(byte)(right>>8);
  }
 }
}
