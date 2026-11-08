package com.example.beautifuldecor.client;
public class HsVideoPlayer { public static final int IDLE=0, LOADING=1, PLAYING=2, PAUSED=3, ERROR=4; public static HsVideoPlayer get(){return null;}
 public void play(String u, java.nio.file.Path f, String t, net.minecraft.core.BlockPos p, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> d){}
 public void stop(){} public void update(){} public boolean active(){return false;} public int state(){return 0;} public void togglePause(){} public float loadProgress(){return 0;} public String message(){return "";} public String notice(){return "";}
 public boolean draw(net.minecraft.client.gui.GuiGraphics g,int a,int b,int c,int d){return false;} }
