package com.example.beautifuldecor.client;
public class HsAudioPlayer { public static final int IDLE=0, LOADING=1, PLAYING=2, PAUSED=3, ERROR=4; public static HsAudioPlayer get(){return null;}
 public void play(String u, java.nio.file.Path f, String t, net.minecraft.core.BlockPos p, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> d){}
 public void stop(){} public int state(){return 0;} public void togglePause(){} public float loadProgress(){return 0;} public String title(){return "";} public String message(){return "";}
 public long elapsedMs(){return 0;} public boolean live(){return false;} public float volume(){return 0;} public void setVolume(float v){} }
