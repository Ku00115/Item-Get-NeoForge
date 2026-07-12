package com.kuzhi.itemget.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class SelectionListScreen extends CrispScreen {
    public record Entry(String id, String name) {}
    private final Screen parent; private final List<Entry> all, shown = new ArrayList<>(); private final Consumer<String> selected;
    private final List<AbstractWidget> rows = new ArrayList<>(); private EditBox search; private int scroll;
    public SelectionListScreen(Screen parent, String title, List<Entry> entries, Consumer<String> selected) { super(Component.literal(title)); this.parent = parent; this.all = entries; this.selected = selected; }
    @Override protected void init() { search = new EditBox(font, width/2-150, 25, 300, 20, Component.translatable("item_get.picker.search")); search.setHint(Component.translatable("item_get.picker.search")); search.setResponder(v -> filter()); addRenderableWidget(search); filter(); setInitialFocus(search); }
    private int count() { return Math.max(4, Math.min(12, (height-70)/23)); }
    private void filter() { String q=search==null?"":search.getValue().toLowerCase(Locale.ROOT); shown.clear(); for(Entry e:all) if(q.isBlank()||e.id.contains(q)||e.name.toLowerCase(Locale.ROOT).contains(q)) shown.add(e); scroll=0; rows(); }
    private void rows() { rows.forEach(this::removeWidget); rows.clear(); int left=width/2-150, top=53; for(int i=0;i<count()&&scroll+i<shown.size();i++){ Entry e=shown.get(scroll+i); Button b=Button.builder(Component.literal(e.name+"   "+e.id), x->{selected.accept(e.id);minecraft.setScreen(parent);}).bounds(left,top+i*23,300,20).build();rows.add(b);addRenderableWidget(b);} }
    @Override public boolean mouseScrolled(double x,double y,double scrollX,double scrollY){int old=scroll;scroll=Math.max(0,Math.min(Math.max(0,shown.size()-count()),scroll-(int)Math.signum(scrollY)));if(old!=scroll)rows();return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float p){g.fill(0, 0, width, height, 0x88000000);g.drawCenteredString(font,title,width/2,9,0xFFFFFF);super.render(g,mx,my,p);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
