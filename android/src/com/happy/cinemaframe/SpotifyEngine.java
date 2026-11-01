package com.happy.cinemaframe;

import android.content.Context;
import android.media.*;
import android.system.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Embedded go-librespot transport. PCM pipe design adapted from LibreThing (GPL-3.0). */
public final class SpotifyEngine {
    private final Context context;private volatile boolean running;private Process process;private volatile long generationCounter=0;private volatile AudioTrack output;private volatile int volume=100;
    public volatile String error="";
    SpotifyEngine(Context c){context=c.getApplicationContext();volume=context.getSharedPreferences("setup",0).getInt("app_volume",100);}
    public int getVolume(){return volume;}
    public void setVolume(int value){if(value<0||value>100)throw new IllegalArgumentException("Volume out of range");volume=value;context.getSharedPreferences("setup",0).edit().putInt("app_volume",value).apply();AudioTrack active=output;if(active!=null)try{active.setVolume(value/100f);}catch(IllegalStateException ignored){}}
    public synchronized void start(){
        if(running)return;final long generation=++generationCounter;running=true;error="";
        new Thread(()->{
            try{
                File dir=new File(context.getFilesDir(),"spotify-engine");dir.mkdirs();File fifo=new File(dir,"audio.pipe");
                if(fifo.exists())fifo.delete();Os.mkfifo(fifo.getAbsolutePath(),0600);
                String network="";
                for(NetworkInterface ni:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InterfaceAddress ia:ni.getInterfaceAddresses())if(ia.getAddress() instanceof Inet4Address && !ia.getAddress().isLoopbackAddress()){
                    network="android_net_iface_name: \""+ni.getName()+"\"\nandroid_net_iface_index: "+ni.getIndex()+"\nandroid_net_ip: \""+ia.getAddress().getHostAddress()+"\"\nandroid_net_prefix_len: "+ia.getNetworkPrefixLength()+"\n";
                    if(ni.getName().equals("wlan0"))break;
                }
                String config="log_level: \"error\"\ndevice_name: \"Cinema Frame\"\ndevice_type: \"speaker\"\nbitrate: 160\nprefer_firewall_friendly_ports: true\naudio_backend: \"pipe\"\naudio_output_pipe: \""+fifo.getAbsolutePath()+"\"\naudio_output_pipe_format: \"s16le\"\naudio_output_pipe_wait_for_reader: true\ninitial_volume: 100\nvolume_steps: 100\nignore_last_volume: false\ndisable_autoplay: true\nzeroconf_enabled: false\n"+network+"credentials:\n  type: \"device_auth\"\nserver:\n  enabled: true\n  address: \"127.0.0.1\"\n  port: 24881\ncache:\n  enabled: false\n";
                try(FileOutputStream f=new FileOutputStream(new File(dir,"config.yml"))){f.write(config.getBytes("UTF-8"));}
                new Thread(()->readAudio(fifo,generation),"cinema-audio").start();
                ProcessBuilder pb=new ProcessBuilder(new File(context.getApplicationInfo().nativeLibraryDir,"libgolibrespot.so").getAbsolutePath(),"--config_dir",dir.getAbsolutePath());
                pb.directory(dir);pb.redirectErrorStream(true);pb.environment().put("XDG_CONFIG_HOME",dir.getAbsolutePath());
                Process child=pb.start();process=child;
                try(BufferedReader r=new BufferedReader(new InputStreamReader(child.getInputStream()))){String line;while((line=r.readLine())!=null){if(line.toLowerCase().contains("fatal"))error="재생 엔진 시작 오류";}}
                int exit=child.waitFor();if(running&&generation==generationCounter)error="재생 엔진 종료: "+exit;
            }catch(Exception e){if(generation==generationCounter)error=e.getClass().getSimpleName()+": "+e.getMessage();}finally{if(generation==generationCounter)running=false;}
        },"cinema-engine").start();
    }
    private void readAudio(File fifo,long generation){AudioTrack audio=null;
        try{
            int min=Math.max(4096,AudioTrack.getMinBufferSize(44100,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT));
            audio=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(min*4).setTransferMode(AudioTrack.MODE_STREAM).build();
            output=audio;audio.setVolume(volume/100f);audio.play();byte[] buf=new byte[8192];boolean focus=false;
            while(running&&generation==generationCounter){
                FileDescriptor fd=Os.open(fifo.getAbsolutePath(),OsConstants.O_RDONLY|OsConstants.O_NONBLOCK,0);
                try{StructPollfd poll=new StructPollfd();poll.fd=fd;poll.events=(short)OsConstants.POLLIN;
                    while(running&&generation==generationCounter){if(Os.poll(new StructPollfd[]{poll},200)<=0)continue;int n;try{n=Os.read(fd,buf,0,buf.length);}catch(ErrnoException e){if(e.errno==OsConstants.EAGAIN)continue;throw e;}
                        if(n<=0){Thread.sleep(100);break;}
                        if(!focus){((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(null,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);focus=true;}
                        int off=0;while(off<n){int w=audio.write(buf,off,n-off);if(w<=0)break;off+=w;}
                    }
                }finally{Os.close(fd);}
            }
        }catch(Exception e){if(running&&generation==generationCounter)error="오디오 출력: "+e.getMessage();}finally{if(output==audio)output=null;if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();}}
    }
    public boolean isRunning(){return running;}
    public synchronized void stop(){generationCounter++;running=false;if(process!=null)process.destroy();}
}
