package ds.mods.OCLights2.block.tileentity;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import li.cil.oc.api.network.Arguments;
import li.cil.oc.api.network.Callback;
import li.cil.oc.api.network.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;

import org.apache.commons.lang3.ArrayUtils;

import com.google.common.collect.ImmutableSortedSet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import ds.mods.OCLights2.OCLights2;
import ds.mods.OCLights2.CommandEnum;
import ds.mods.OCLights2.converter.ConvertDouble;
import ds.mods.OCLights2.converter.ConvertInteger;
import ds.mods.OCLights2.converter.ConvertString;
import ds.mods.OCLights2.gpu.DrawCMD;
import ds.mods.OCLights2.gpu.GPU;
import ds.mods.OCLights2.gpu.Monitor;
import ds.mods.OCLights2.gpu.Texture;
import ds.mods.OCLights2.network.PacketSenders;

public class TileEntityGPU extends TileEntity implements SimpleComponent {
	public GPU gpu;
    private ArrayList<DrawCMD> newarr = new ArrayList<DrawCMD>();
	public ArrayList<IComputerAccess> comp = new ArrayList<IComputerAccess>();
	private TreeMap<String, Integer> playerToClickMap = new TreeMap<String, Integer>();
	private TreeMap<Integer, int[]> clickToDataMap = new TreeMap<Integer, int[]>();
	public int[] addedType = new int[1025];
	private boolean frame = false;
	private byte ticks = 0;
	private boolean sentOnce = false;
	public static final CommandEnum[] EnumCache = CommandEnum.values();

	public TileEntityGPU() {
		gpu = new GPU(1024 * 8);
		gpu.tile = this;
	}

	public void startClick(Player player, int button, int x, int y) {
		int id = new Random().nextInt();
		while (playerToClickMap.containsValue(id)) {
			id = new Random().nextInt();
		}
		playerToClickMap.put(((EntityPlayer) player).username, id);
		clickToDataMap.put(id, new int[] { button, x, y });

		String event = "monitor_down";
		Object[] args = new Object[] { button, x, y, id };
		for (IComputerAccess c : comp) {
			c.queueEvent(event, args);
		}
	}

	public void moveClick(Player player, int nx, int ny) {
		int id = playerToClickMap.get(((EntityPlayer) player).username);
		int[] data = clickToDataMap.get(id);
		int button = data[0];
		data[1] = nx;
		data[2] = ny;

		String event = "monitor_move";
		Object[] args = new Object[] { button, nx, ny, id };
		for (IComputerAccess c : comp) {
			c.queueEvent(event, args);
		}
	}

	public void endClick(Player player) {
		int id = playerToClickMap.get(((EntityPlayer) player).username);
		int[] data = clickToDataMap.get(id);
		int button = data[0];
		int x = data[1];
		int y = data[2];

		String event = "monitor_up";
		Object[] args = new Object[] { button, x, y, id };
		for (IComputerAccess c : comp) {
			c.queueEvent(event, args);
		}
		playerToClickMap.remove(((EntityPlayer) player).username);
		clickToDataMap.remove(id);
	}

	@Override
	public String getComponentName() {
		return "GPU";
	}

	@Callback
	public Object[] fill(Context context, Arguments args) {
			//fill
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = CommandEnum.Fill;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] createTexture(Context context, Arguments args) {
			//createTexture
			if (args.count() > 1) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0),
						args.checkInteger(1) };
				cmd.cmd = CommandEnum.CreateTexture;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				int id = (Integer) ret[0];
				if (id == -1) {
					throw new Exception("createTexture: Not enough memory");
				} else if (id == -2) {
					throw new Exception("createTexture: Not enough texture slots");
				} else {
					gpu.drawlist.push(cmd);
					return ret;
				}
			}
			else
			{
				throw new Exception("createTexture: Argument Error: width, height expected");
			}
		}
	
	@Callback
	public Object[] getFreeMemory(Context context, Arguments args) {
			//getFreeMemory
			return new Object[] { gpu.getFreeMemory() };
		}
	
	@Callback
	public Object[] getTotalMemory(Context context, Arguments args) {
			//getTotalMemory
			return new Object[] { gpu.maxmem };
		}
	
	@Callback
	public Object[] getUsedMemory(Context context, Arguments args) {
			//getUsedMemory
			return new Object[] { gpu.getUsedMemory() };
		}
	
	@Callback
	public Object[] bindTexture(Context context, Arguments args) {
			//bindTexture
			if (args.count() > 0) {
				if (gpu.textures[args.checkInteger(0)] == null)
					throw new Exception("bindTexture: Texture does not exist");
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0) };
				cmd.cmd = CommandEnum.BindTexture;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("bindTexture: Argument Error: textureid expected");
			}
			break;
		}
	
	@Callback
	public Object[] plot(Context context, Arguments args) {
			//was plot and setColorRGB is now plot
			if (args.count() >= 2) {
				int x = args.checkInteger(0);
				int y = args.checkInteger(1);
				Point2D point = gpu.transform.transform(new Point2D.Double(x, y),null);
				double tx = point.getX();
				double ty = point.getY();
				int w = gpu.bindedTexture.getWidth();
				int h = gpu.bindedTexture.getHeight();
				if (tx<0 || ty<0 || tx>w || ty>h) //Don't draw if out of bounds!
					return null;
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { x, y };
				cmd.cmd = CommandEnum.Plot;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("plot: Argument Error: x, y expected");
			}
			break;
		}
	
	@Callback
	public Object[] drawTexture(Context context, Arguments args) {
			//drawTexture
			if (args.count() == 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { 0, args.checkInteger(0),
						args.checkInteger(1), args.checkInteger(2) };
				cmd.cmd = CommandEnum.DrawTexture;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			} else if (args.count() > 6) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { 1, args.checkInteger(0),
						args.checkInteger(1), args.checkInteger(2),
						args.checkInteger(3), args.checkInteger(4),
						args.checkInteger(5), args.checkInteger(6) };
				cmd.cmd = CommandEnum.DrawTexture;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("drawTexture: Argument Error: textureid, x, y expected");
			}
			break;
		}
	
	@Callback
	public Object[] freeTexture(Context context, Arguments args) {
			//freeTexture
			if (args.count() == 1) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0) };
				cmd.cmd = CommandEnum.FreeTexture;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("freeTexture: Argument Error: textureid expected");
			}
			break;
		}
	
	@Callback
	public Object[] line(Context context, Arguments args) {
			//line
			if (args.count() > 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0),
						args.checkInteger(1), args.checkInteger(2),
						args.checkInteger(3) };
				cmd.cmd = CommandEnum.Line;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("line: Argument Error: x1, y1, x2, y2 expected");
			}
			break;
		}
	
	@Callback
	public Object[] getSize(Context context, Arguments args) {
			//getSize
			int tex = gpu.bindedSlot;
			if (args.count() >= 1)
			{
				tex = args.checkInteger(0);
			}
			if (gpu.textures[tex] == null)
				throw new Exception("getMonitorSize: texture does not exist");
			Texture texture = gpu.textures[tex];
			return new Object[] { texture.getWidth(),texture.getHeight() };
		}
	
	@Callback
	public Object[] getPixelColor(Context context, Arguments args) {
			//getPixelColor
			if (args.count() > 1) {
				int x = args.checkInteger(0);
				int y = args.checkInteger(1);
				int[] dat = gpu.bindedTexture.getRGB(x, y);
				return new Object[] { dat[0] & 0xFF, dat[1] & 0xFF, dat[2] & 0xFF, dat[3] & 0xFF };
			}
			else
			{
				throw new Exception("getPixelColor: Argument Error: x, y expected");
			}
		}
	
	@Callback
	public Object[] gectangle(Context context, Arguments args) {
			//rectangle
			if (args.count() > 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0),
						args.checkInteger(1), args.checkInteger(2),
						args.checkInteger(3) };
				cmd.cmd = CommandEnum.Rectangle;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("rectangle: Argument Error: x, y, width, height expected");
			}
			break;
		}
	
	@Callback
	public Object[] filledRectangle(Context context, Arguments args) {
			//filledrectangle
			if (args.count() > 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0),
						args.checkInteger(1), args.checkInteger(2),
						args.checkInteger(3) };
				cmd.cmd = CommandEnum.FilledRectangle;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("filledRectangle: Argument Error: x, y, width, height expected");
			}
			break;
		}
	
	@Callback
	public Object[] getBindedTexture(Context context, Arguments args) {
			//getBindedTexture
			return new Object[] { gpu.bindedSlot };
		}
	
	@Callback
	public Object[] setPixels(Context context, Arguments args) {
			//setPixels
			if (args.count() < 4) {
				throw new Exception("setPixelsRaw: Argument Error: w, h, x, y, {[r,g,b,a]}... expected");
			} else {
				int w = args.checkInteger(0);
				int h = args.checkInteger(1);
				// We send the arguments straight to the GPU!
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[(w * h * 4) + 4 + 1];
				nargs[0] = 0;
				nargs[1] = w;
				nargs[2] = h;
				nargs[3] = args.checkInteger(2);
				nargs[4] = args.checkInteger(3);
				Map m = args.checkTable(4);
				for (int i = 1; i <= (w * h * 4); i++) {
                 nargs[i + 4] = ConvertInteger.convert(m.get((double) i)).intValue();
				}
				cmd.cmd = CommandEnum.SetPixels;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
	
	@Callback
	public Object[] flipVertically(Context context, Arguments args) {
			//flipTextureV
			if (args.count() > 0) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0) };
				cmd.cmd = CommandEnum.FlipVertically;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else{
				throw new Exception("Number expected.");
			}
		}
	
	@Callback
	public Object[] Import(Context context, Arguments args) {
			//import
			double a = System.currentTimeMillis();
			Byte[] data;
			if (args.count() == 1 && args.isTable(0))
			{
				//One of the things I hate is that ComputerCraft uses Doubles for all their values
				//Double the fun! -alekso56
				Map m = args.checkTable(0);
				data = new Byte[m.size()];
				for (double i = 0; i<data.length; i++)
				{
					data[(int) i] = ((Double)m.get(i+1D)).byteValue();
				}
			}
			else if (args.count() == 1 && args.isString(0))
			{
				String file = args.checkString(0);
				File f = new File(OCLights2.proxy.getWorldDir(worldObj),"computer/"+computer.getID()+"/"+file);
				FileInputStream in = new FileInputStream(f);
				byte[] b = new byte[(int)in.getChannel().size()];
				in.read(b);
				in.close();
				data = ArrayUtils.toObject(b);
			}
			else
			{
				throw new Exception("import: Argument Error: (filedata or filename)");
			}
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[]{data};
			cmd.cmd = CommandEnum.Import;
			cmd.args = nargs;
			int id = (Integer) gpu.processCommand(cmd)[0];
			Texture tex = gpu.textures[id];
			Object[] ret = {id,tex.getWidth(),tex.getHeight()};
			gpu.drawlist.push(cmd);
			double b = System.currentTimeMillis();
			OCLights2.debug("Import time: "+(b-a)+"ms");
			return ret;
		}
	
	@Callback
	public Object[] export(Context context, Arguments args) {
			//export
			if (args.count() > 1)
			{
				int texid = args.checkInteger(0);
				String format = args.checkString(1);
				if (texid<0 || texid>gpu.textures.length || gpu.textures[texid] == null)
				{
					throw new Exception("Texture does not exist.");
				}
				Texture tex = gpu.textures[texid];
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write(tex.img, format, output);
				byte[] data = output.toByteArray();
				HashMap<Double,Double> out = new HashMap<Double, Double>();
				for (int i = 0; i<data.length; i++)
				{
					out.put((double)(i+1), (double)data[i]);
				}
				return new Object[]{out};
			}
			else
			{
				throw new Exception("export: Argument Error: textureid, format expected");
			}
		}
	
	@Callback
	public Object[] drawText(Context context, Arguments args) {
			//Drawtext
			if (args.count() > 2)
			{
				String str = args.checkString(0);
				int x = args.checkInteger(1);
				int y = args.checkInteger(2);
				Point2D point = gpu.transform.transform(new Point2D.Double(x, y),null);
				double tx = point.getX();
				double ty = point.getY();
				int w = gpu.bindedTexture.getWidth();
				int h = gpu.bindedTexture.getHeight();
				double tw = Texture.getStringWidth(str);
				double th = 8;
				if ((tx<0 && tx+tw<0) || (ty<0 && ty+th<0) || (tx>w) || (ty>h)) //Don't draw if out of bounds!
				{
					return null;
				}
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[2+str.length()];
				nargs[0] = x;
				nargs[1] = y;
				for (int i=0; i<str.length(); i++)
				{
					nargs[2+i] = str.charAt(i);
				}
				cmd.cmd = CommandEnum.DrawTexture;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("drawText: Argument Error: text, x, y expected");
			}
		}
	
	@Callback
	public Object[] getTextWidth(Context context, Arguments args) {
			//getTextWidth
			if (args.count() > 0)
			{
				String str = args.checkString(0);
				return new Object[]{Texture.getStringWidth(str)};
			}
			else
			{
				throw new Exception("getTextWidth: Argument Error: text expected");
			}
		}
	
	@Callback
	public Object[] setColor(Context context, Arguments args) {
			//setColor
			if (args.count() > 2)
			{
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[4];
				for (int i=0; i<4; i++)
				{
					nargs[i] = args.count() > i ? args.checkInteger(i) : 255;
				}
				if (gpu.color.getRed() == (Integer)nargs[0] && gpu.color.getBlue() == (Integer)nargs[1] && gpu.color.getGreen() == (Integer)nargs[2] && gpu.color.getAlpha() == (Integer)nargs[3])
				{
					break;
				}
				cmd.cmd = CommandEnum.SetColor;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				break;
			}
			else
			{
				throw new Exception("setColor: Argument Error: int, int, int[, int] expected");
			}
		}
	
	@Callback
	public Object[] getColor(Context context, Arguments args) {
			//getColor
			return new Object[]{gpu.color.getRed(),gpu.color.getGreen(),gpu.color.getBlue(),gpu.color.getAlpha()};
		}
	
	@Callback
	public Object[] translate(Context context, Arguments args) {
			//translate
			double x = args.checkDouble(0);
			double y = args.checkDouble(1);
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[2];
			nargs[0] = x;
			nargs[1] = y;
			cmd.cmd = CommandEnum.Transelate;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] rotate(Context context, Arguments args) {
			//rotate
			double r = args.checkDouble(0);
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[1];
			nargs[0] = r;
			cmd.cmd = CommandEnum.Rotate;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] rotateAround(Context context, Arguments args) {
			//rotateAround
			double r = args.checkDouble(0);
			double x = args.checkDouble(1);
			double y = args.checkDouble(2);
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[3];
			nargs[0] = r;
			nargs[1] = x;
			nargs[2] = y;
			cmd.cmd = CommandEnum.RotateAround;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] scale(Context context, Arguments args) {
			//scale
			double x = args.checkDouble(0);
			double y = args.checkDouble(1);
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[2];
			nargs[0] = x;
			nargs[1] = y;
			cmd.cmd = CommandEnum.Scale;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] push(Context context, Arguments args) {
			//push
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = CommandEnum.Push;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] pop(Context context, Arguments args) {
			//pop
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = CommandEnum.Pop;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
	
	@Callback
	public Object[] getMonitor(Context context, Arguments args) {
			//getMonitor
			return new Object[]{gpu.currentMonitor.obj};
		}
	
	@Callback
	public Object[] blur(Context context, Arguments args) {
			//blur
			if (args.count() > 0) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0) };
				cmd.cmd = CommandEnum.Blur;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("blur: Argument Error: textureid expected");
			}
		}
	
	@Callback
	public Object[] startFrame(Context context, Arguments args) {
			//startFrame
			frame = true;
			break;
		}
	
	@Callback
	public Object[] endFrame(Context context, Arguments args) {
			//endFrame
			frame = false;
			break;
		}
	
	@Callback
	public Object[] ClearRectangle(Context context, Arguments args) {
			//clearRect
			if (args.count() >= 4) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { args.checkInteger(0),args.checkInteger(1),args.checkInteger(2),args.checkInteger(3) };
				cmd.cmd = CommandEnum.ClearRectangle;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("clearRect: Argument Error: x, y, width, height expected");
			}
		}
	
	@Callback
	public Object[] origin(Context context, Arguments args) {
			//origin
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] {};
			cmd.cmd = CommandEnum.Origin;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
	
	@Override
	public void attach(IComputerAccess computer) {
		comp.add(computer);
		computer.mount("oclights2", new IMount(){
			private static final String RESOURCE_PATH = "/assets/oclights/lua/";
			private final SortedSet<String> files;

			{
				ImmutableSortedSet.Builder<String> files = ImmutableSortedSet.naturalOrder();
				InputStream fileList = getClass().getResourceAsStream(RESOURCE_PATH + "files.lst");
				if (fileList != null) {
					Scanner sc = new Scanner(fileList);

					while (sc.hasNextLine()) {
						String fileName = sc.nextLine();
						files.add(fileName);
					}

					sc.close();
				}

				this.files = files.build();
			}

			@Override
			public boolean exists(String path) throws IOException {
				return path.isEmpty() || files.contains(path);
			}

			@Override
			public boolean isDirectory(String path) throws IOException {
				return path.isEmpty();
			}

			@Override
			public void list(String path, List<String> contents) throws IOException {
				contents.addAll(files);
			}

			@Override
			public long getSize(String path) throws IOException {
				return 0;
			}

			@Override
			public InputStream openForRead(String path) throws IOException {
				if (!files.contains(path)) throw new IOException();
				return getClass().getResourceAsStream(RESOURCE_PATH + path);
			}

		});
	}

	@Override
	public void detach(IComputerAccess computer) {
		comp.remove(computer);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setIntArray("addedTypes", addedType);
		nbt.setInteger("vram", gpu.maxmem);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		addedType = nbt.getIntArray("addedTypes");
		if (addedType == null) {
			addedType = new int[1025];
		} else if (addedType.length != 1025) {
			addedType = new int[1025];
		}
		int init = gpu.maxmem;
		gpu.maxmem = nbt.getInteger("vram");
		if (init > gpu.maxmem) {
			gpu.maxmem = init;
		}
	}
	
	public void connectToMonitor() {
		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++) {
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
			TileEntity ftile = worldObj.getBlockTileEntity(
					xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord
							+ dir.offsetZ);
			if (ftile != null) {
				if (ftile instanceof TileEntityMonitor) {
					TileEntityMonitor tile = (TileEntityMonitor) worldObj
							.getBlockTileEntity(xCoord + dir.offsetX, yCoord
									+ dir.offsetY, zCoord + dir.offsetZ);
					if (tile != null) {
						boolean found = false;
						for (Monitor m : gpu.monitors) {
							if (m == tile.mon) {
								found = true;
								break;
							}
						}
						if (found)
							break;
						tile.connect(this.gpu);
						tile.mon.tex.fill(Color.black);
						tile.mon.tex.drawText("Monitor connected", 0, 0, Color.white);
						tile.mon.tex.texUpdate();
						gpu.setMonitor(tile.mon);
						return;
					}
				} 
			}
		}
	}

	@Override
	public synchronized void updateEntity() {
		synchronized (this) {if (!frame){ gpu.processSendList();}}
		connectToMonitor();
		if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT && ticks++ % 20 == 0 && !sentOnce) {
		PacketSenders.GPUDOWNLOAD(xCoord, yCoord, zCoord);
		sentOnce=true;
		}

	}

	/* @Override
	public boolean equals(SimpleComponent other) {
		if(other.getComponentName() == getComponentName()){return true;}
		else return false;
	} */
}