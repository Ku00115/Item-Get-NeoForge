package com.kuzhi.itemget.client.screen;

import com.kuzhi.itemget.client.AudioHelper;
import com.kuzhi.itemget.client.ClientEvents;
import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

public final class ReminderScreen extends Screen {
    private static final ResourceLocation ITEM_GLOW=ResourceLocation.fromNamespaceAndPath("item_get","textures/gui/item_glow.png");
    private final ReminderRule rule; private final Screen returnTo; private int ticks; private boolean soundPlayed; private long openedAt=-1L;private int descriptionScroll,maxDescriptionScroll;
    public ReminderScreen(ReminderRule rule,Screen returnTo){super(Component.literal(rule.title==null?"":rule.title));this.rule=rule;this.returnTo=returnTo;}
    @Override protected void init(){if(openedAt<0)openedAt=Util.getMillis();if(!soundPlayed){soundPlayed=true;if(rule.sound!=null&&!rule.sound.isBlank())AudioHelper.play(rule.sound);}}
    @Override public void tick(){ticks++;}
    @Override public boolean isPauseScreen(){return rule.pauseSingleplayer&&minecraft!=null&&minecraft.hasSingleplayerServer();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(ClientEvents.CLOSE.matches(key,scan)&&elapsed()>=closeStart()){close();return true;}return false;}
    @Override public boolean mouseClicked(double x,double y,int button){return false;}
    @Override public boolean mouseScrolled(double x,double y,double scrollX,double scrollY){if(elapsed()<typewriterEnd())return true;if(maxDescriptionScroll>0){descriptionScroll=Math.max(0,Math.min(maxDescriptionScroll,descriptionScroll-(int)Math.signum(scrollY)));return true;}return false;}
    private void close(){minecraft.setScreen(returnTo);}@Override public void onClose(){close();}

    @Override public void render(GuiGraphics g,int mx,int my,float partial){float time=elapsed();float p=clamp(time/360F);float scale=.92F+.08F*(1F-(float)Math.pow(1F-p,3));renderHorizontal(g,time,scale);}

    private void renderHorizontal(GuiGraphics g,float time,float scale){
        int cx=width/2,cy=height/2-8,panelWidth=Math.min(350,width-26),left=cx-panelWidth/2,right=cx+panelWidth/2,top=Math.max(10,cy-56),bottom=Math.min(height-10,cy+56);
        int iconX=left+Math.min(76,panelWidth/4),textLeft=left+Math.min(132,panelWidth*2/5),textRight=right-14,textCenter=(textLeft+textRight)/2,textWidth=Math.max(80,textRight-textLeft);
        var lines=font.split(Component.literal(visibleDescription(time)),textWidth);
        float panelFade=fade(time,0,260);pushScale(g,cx,cy,scale);softPanel(g,left,top,right,bottom,panelFade);
        glow(g,iconX,cy-3,82,panelFade);var stack=ManagerScreen.displayStack(rule);g.pose().pushPose();g.pose().translate(iconX,cy-3,0);g.pose().scale(2.7F,2.7F,1);g.renderItem(stack,-8,-8);g.pose().popPose();
        float headingFade=fade(time,160,280);drawCentered(g,rule.title,textCenter,top+14,0xFFE9B0,headingFade);drawCentered(g,ManagerScreen.triggerSummary(rule),textCenter,top+32,0xFFFFFF,headingFade);
        drawDescription(g,lines,textCenter,top+48,4,right-7,time);
        float closeFade=fade(time,closeStart(),280);if(closeFade>=.04F)drawCentered(g,Component.translatable("item_get.continue",ClientEvents.CLOSE.getTranslatedKeyMessage()),textCenter,bottom-13,0x9099A2,closeFade);g.pose().popPose();
    }

    private void pushScale(GuiGraphics g,int cx,int cy,float scale){g.pose().pushPose();g.pose().translate(cx,cy,100);g.pose().scale(scale,scale,1);g.pose().translate(-cx,-cy,0);}
    private void softPanel(GuiGraphics g,int left,int top,int right,int bottom,float fade){int topAlpha=(int)(190*fade),bottomAlpha=(int)(150*fade);g.fillGradient(left,top,right,bottom,0,topAlpha<<24,bottomAlpha<<24);}
    private void glow(GuiGraphics g,int cx,int cy,int size,float alpha){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1F,1F,1F,alpha);g.blit(ITEM_GLOW,cx-size/2,cy-size/2,0,0,size,size,size,size);RenderSystem.setShaderColor(1F,1F,1F,1F);RenderSystem.disableBlend();}
    private void drawDescription(GuiGraphics g,java.util.List<FormattedCharSequence> lines,int cx,int startY,int visible,int barX,float time){maxDescriptionScroll=Math.max(0,lines.size()-visible);if(time<typewriterEnd())descriptionScroll=maxDescriptionScroll;else descriptionScroll=Math.min(descriptionScroll,maxDescriptionScroll);int count=Math.min(visible,lines.size()-descriptionScroll);float alpha=fade(time,360,160);for(int i=0;i<count;i++)drawCentered(g,lines.get(descriptionScroll+i),cx,startY+i*11,0xD0D7DF,alpha);if(maxDescriptionScroll>0){int track=Math.max(10,visible*11-3),thumb=Math.max(8,track*visible/lines.size()),thumbY=startY+(track-thumb)*descriptionScroll/maxDescriptionScroll;int a=(int)(110*fade(time,typewriterEnd(),260));g.fill(barX,startY,barX+1,startY+track,(a/3)<<24|0xFFFFFF);g.fill(barX-1,thumbY,barX+2,thumbY+thumb,a<<24|0xFFFFFF);}}
    private void drawCentered(GuiGraphics g,String text,int x,int y,int rgb,float alpha){if(text==null||text.isBlank()||alpha<=0)return;g.drawCenteredString(font,text,x,y,color(rgb,alpha));}
    private void drawCentered(GuiGraphics g,Component text,int x,int y,int rgb,float alpha){if(alpha<=0)return;g.drawCenteredString(font,text,x,y,color(rgb,alpha));}
    private void drawCentered(GuiGraphics g,FormattedCharSequence text,int x,int y,int rgb,float alpha){if(alpha<=0)return;g.drawCenteredString(font,text,x,y,color(rgb,alpha));}
    private long elapsed(){return openedAt<0?0:Math.max(0,Util.getMillis()-openedAt);}private static float clamp(float value){return Math.max(0,Math.min(1,value));}
    private String fullDescription(){return rule.description==null?"":rule.description;}private float typewriterDuration(){return Math.max(260,Math.min(2400,fullDescription().length()*14F));}private float typewriterEnd(){return 360+typewriterDuration();}private float closeStart(){return typewriterEnd()+180;}
    private String visibleDescription(float time){String full=fullDescription();if(full.isEmpty())return full;int count=(int)(full.length()*clamp((time-360)/typewriterDuration()));return full.substring(0,Math.min(full.length(),count));}
    private static int color(int rgb,float alpha){return(((int)(255*clamp(alpha)))<<24)|(rgb&0xFFFFFF);}private static float fade(float time,float start,float duration){float p=clamp((time-start)/duration);return p*p*(3-2*p);}
}
