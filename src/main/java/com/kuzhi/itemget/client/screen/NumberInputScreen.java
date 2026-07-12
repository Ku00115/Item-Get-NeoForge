package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class NumberInputScreen extends CrispScreen {
    private final Screen parent; private final String label, initial; private final Consumer<String> selected; private EditBox value;
    public NumberInputScreen(Screen parent,String label,String initial,Consumer<String> selected){super(Component.literal(label));this.parent=parent;this.label=label;this.initial=initial;this.selected=selected;}
    @Override protected void init(){int x=width/2-100,y=height/2-10;value=new EditBox(font,x,y,200,20,Component.literal(label));value.setValue(initial);addRenderableWidget(value);addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{selected.accept(value.getValue());minecraft.setScreen(parent);}).bounds(x,y+28,98,20).build());addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->minecraft.setScreen(parent)).bounds(x+102,y+28,98,20).build());setInitialFocus(value);}
    @Override public void render(GuiGraphics g,int mx,int my,float p){g.fill(0, 0, width, height, 0x88000000);g.drawCenteredString(font,title,width/2,height/2-28,0xFFFFFF);super.render(g,mx,my,p);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
