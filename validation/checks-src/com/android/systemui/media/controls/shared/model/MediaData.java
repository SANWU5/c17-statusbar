package com.android.systemui.media.controls.shared.model;
public final class MediaData {
    public MediaDeviceData device;public android.graphics.drawable.Icon artwork;public CharSequence song="song",artist="artist";
    public String packageName="player";public Object token=new Object();public boolean resumption,playing;
    public MediaDeviceData getDevice(){return device;}public android.graphics.drawable.Icon getArtwork(){return artwork;}
    public CharSequence getSong(){return song;}public CharSequence getArtist(){return artist;}public String getPackageName(){return packageName;}
    public Object getToken(){return token;}public boolean getResumption(){return resumption;}public Boolean isPlaying(){return playing;}
}
