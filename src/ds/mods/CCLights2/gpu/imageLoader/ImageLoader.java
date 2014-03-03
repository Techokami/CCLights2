package ds.mods.CCLights2.gpu.imageLoader;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ImageLoader {
	private static ArrayList<IImageLoader> loaders = new ArrayList<IImageLoader>();
	
	public static void register(IImageLoader load)
	{
		loaders.add(load);
	}
	
	public static BufferedImage load(byte[] data) throws Exception
	{
		if (loaders.size() == 0)
		{
			register(new GeneralImageLoader());
		}
		for (IImageLoader load : loaders)
		{
			BufferedImage img = load.loadImage(data);
			if (img != null)
			{
				return img;
			}
		}
		throw new Exception("failed to load image");
	}
}
