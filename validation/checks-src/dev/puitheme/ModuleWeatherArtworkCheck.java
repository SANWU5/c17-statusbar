// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import javax.imageio.ImageIO;

/** CPU artwork regression and optional contact sheet; Android/Skia visual QA remains separate. */
public final class ModuleWeatherArtworkCheck {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        WeatherTextIcon.Kind[] kinds=WeatherTextIcon.Kind.values();
        BufferedImage sheet=new BufferedImage(840,((kinds.length+3)/4)*170,BufferedImage.TYPE_INT_ARGB);
        Graphics2D display=sheet.createGraphics();display.setColor(new java.awt.Color(0xff293b4c,true));display.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        StringBuilder overflow=new StringBuilder();
        for(int i=0;i<kinds.length;i++)for(boolean colours:new boolean[]{false,true}){
            WeatherTextIcon.Kind kind=kinds[i];Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setTextSize(48);text.setColor(0xffe7f6ff);text.setAlpha(210);
            WeatherTextIcon.Span live=new WeatherTextIcon.Span(kind,null,colours);
            BigClockPreviewIcon preview=new BigClockPreviewIcon(kind,colours);
            Paint.FontMetricsInt a=new Paint.FontMetricsInt(),b=new Paint.FontMetricsInt();
            int width=live.getSize(text,"x",0,1,a);
            check(width==preview.getSize(text,"x",0,1,b)&&a.ascent==b.ascent&&a.descent==b.descent,kind+" preview/live dimensions");
            Bitmap actual=Bitmap.createBitmap(160,140,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(160,140,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(actual);live.draw(canvas,"x",0,1,30,0,95,140,text);
            preview.draw(new Canvas(expected),"x",0,1,30,0,95,140,text);
            check(actual.sameAs(expected),kind+" preview must match actual live artwork pixels");
            int ink=0,minY=140,maxY=-1,minX=160,maxX=-1;
            for(int y=0;y<140;y++)for(int x=0;x<160;x++)if((actual.pixels[y*160+x]>>>24)>8){ink++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);minX=Math.min(minX,x);maxX=Math.max(maxX,x);}
            check(kind==WeatherTextIcon.Kind.UNKNOWN?ink==0:ink>50,kind+" visible coverage / unknown stays empty");
            if(ink>0&&(minX<30||maxX>=30+width||minY<95+a.ascent||maxY>=95+a.descent))overflow.append(kind).append(':').append(minY).append('-').append(maxY).append(" outside ").append(95+a.ascent).append('-').append(95+a.descent).append(';');
            check(canvas.layers==0&&canvas.bitmapDraws==0&&canvas.getSaveCount()==1,kind+" replay has no offscreen layers or bitmap draws and restores canvas");
            int paths=Path.creationsForCheck,bitmaps=Bitmap.creations;
            Canvas recording=new Canvas();
            for(int frame=0;frame<120;frame++){live.draw(recording,"x",0,1,0,0,90,140,text);preview.draw(recording,"x",0,1,0,0,90,140,text);}
            check(paths==Path.creationsForCheck&&bitmaps==Bitmap.creations,kind+" steady frames reuse prebuilt artwork");
            check(text.getAlpha()==210&&text.getColor()==0xffe7f6ff&&text.getShader()==null&&text.getTextSize()==48,kind+" caller paint preserved");
            LinearGradient shader=new LinearGradient(0,0,0,140,0xff112233,0xffaabbcc,Shader.TileMode.CLAMP);text.setShader(shader);
            live.draw(recording,"x",0,1,0,0,90,140,text);check(text.getShader()==shader,kind+" caller shader preserved");
            BufferedImage tile=new BufferedImage(160,140,BufferedImage.TYPE_INT_ARGB);tile.setRGB(0,0,160,140,actual.pixels,0,160);
            int cx=(i%4)*210,cy=(i/4)*170;
            display.drawImage(tile,cx+(colours?84:-10),cy,110,120,null);
            display.setColor(java.awt.Color.WHITE);display.drawString(kind.name(),cx+10,cy+142);
        }
        display.dispose();
        if(args.length>0){File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();ImageIO.write(sheet,"png",file);}
        check(overflow.length()==0,"Artwork stays within the unchanged text span: "+overflow);
        System.out.println("ModuleWeatherArtworkCheck "+checks+" checks passed");
    }
}
