package ds.mods.CCLights2.client.gui;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityMonitor;
import ds.mods.CCLights2.client.render.TabletRenderer;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;
import ds.mods.CCLights2.utils.TabMesg;

public class GuiTablet extends GuiScreen {
	public Monitor mon;
	public Texture tex = TabletRenderer.defaultTexture;
	public NBTTagCompound nbt;
	public TileEntityMonitor tile;
	
	public boolean isMouseDown = false;
	public int mouseButton = 0;
	public int mlx;
	public int mly;
	public int mx;
	public int my;
	
	public GuiTablet(NBTTagCompound n, World world)
	{
		nbt = n;
		if (nbt.getBoolean("canDisplay"))
		{
			UUID trans = UUID.fromString(nbt.getString("trans"));
			tile = (TileEntityMonitor) Minecraft.getMinecraft().theWorld.getBlockTileEntity((Integer)TabMesg.getTabVar(trans, "x"),(Integer)TabMesg.getTabVar(trans, "y"),(Integer)TabMesg.getTabVar(trans, "z"));
			mon = tile.mon;
			tex = mon.tex;
		}
	}
	
	public void initGui()
	{
		CCLights2.debug("Created textures");
		Keyboard.enableRepeatEvents(true);
	}
	
	public int applyXOffset(int x)
	{
		return x-((width/2)-tex.getWidth()/2);
	}
	
	public int applyYOffset(int y)
	{
		return y-((height/2)-tex.getHeight()/2);
	}
	
	public int unapplyXOffset(int x)
	{
		return x+((width/2)-tex.getWidth()/2);
	}
	
	public int unapplyYOffset(int y)
	{
		return y+((height/2)-tex.getHeight()/2);
	}
	
	public void drawScreen(int par1, int par2, float par3)
    {
		nbt.setBoolean("gui", true);
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (nbt.getBoolean("canDisplay"))
		{
			int wheel = Mouse.getDWheel();
			if (wheel != 0)
			{
              PacketSenders.GPUEvent(par1, par2, tile, wheel);
			}
			if (isMouseDown)
			{
				if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
				{
					mx = par1;
					my = par2;
					if (mlx != mx | mly != my)
					{
						CCLights2.debug("Moused move!");
                        PacketSenders.mouseEventMove(mx, my, tile);
					}
					mlx = mx;
					mly = my;
				}
				else
				{
					mouseMovedOrUp(unapplyXOffset(par1)/2, unapplyYOffset(par2)/2, mouseButton);
				}
			}
		}
		drawWorldBackground(0);
		synchronized (tex)
		{
			try {
				if (tex.renderLock) tex.wait(1L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		TextureUtil.uploadTexture(TabletRenderer.dyntex.getGlTextureId(), tex.rgb, 16*32, 9*32);
		this.drawTexturedModalRect(applyXOffset(0)*4, applyYOffset(0)*4, tex.getWidth(), tex.getHeight());
		GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
	
	public void drawTexturedModalRect(int x, int y, int w, int h)
    {
		GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        Tessellator var2 = Tessellator.instance;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        float var3 = 256.0F;
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 1D);
        var2.startDrawingQuads();
        //var2.setColorOpaque_I(4210752);
        var2.addVertexWithUV((double) x, (double) y, this.zLevel, 0.0D, 0D);
        var2.addVertexWithUV(x, (double)h+y, this.zLevel, 0.0D, h/(9*32D));
        var2.addVertexWithUV((double)w+x, (double)h+y, this.zLevel, w/(16*32D), h/(9*32D));
        var2.addVertexWithUV((double)w+x, y, this.zLevel, w/(16*32D), 0D);
        var2.draw();
        GL11.glPopMatrix();
    }
	
	protected void mouseClicked(int par1, int par2, int par3)
    {
		if (!nbt.getBoolean("canDisplay"))
			return;
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (par1 > -1 & par2 > -1 & par1 < mon.getWidth()+1 & par2 < mon.getHeight()+1)
		{
			CCLights2.debug("Mouse click! "+par3);
			isMouseDown = true;
			mouseButton = par3;
			mlx = par1;
			mx = par1;
			mly = par2;
			my = par2;
            PacketSenders.mouseEvent(par1, par2, par3, tile);
		}
    }
	
	protected void mouseMovedOrUp(int par1, int par2, int par3)
    {
		if (!nbt.getBoolean("canDisplay"))
			return;
		par1 = applyXOffset(par1);
		par2 = applyYOffset(par2);
		if (isMouseDown)
		{
			if (par3 == mouseButton)
			{
				CCLights2.debug("Mouse up! "+par3);
				isMouseDown = false;
                PacketSenders.mouseEventUp(tile);
			}
		}
    }

	protected void keyTyped(char par1, int par2)
    {
        super.keyTyped(par1, par2);
        if (par2 != 1 && nbt.getBoolean("canDisplay"))
        {
			  PacketSenders.sendKeyEvent(par1, par2,tile);
        }
    }
	
	public void onGuiClosed()
	{
		Keyboard.enableRepeatEvents(false);
		nbt.setBoolean("gui", false);
	}
	
	public boolean doesGuiPauseGame()
    {
        return false;
    }
}
