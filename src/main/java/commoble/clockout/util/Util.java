package commoble.clockout.util;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldReader;

public class Util
{

	public static <T extends TileEntity> Optional<T> getTileEntityAt(Class<T> clazz, IWorldReader world, BlockPos pos)
	{
		return as(world.getTileEntity(pos), clazz);
	}

	@SuppressWarnings("unchecked")
	public static <T> Optional<T> as(Object thing, Class<T> clazz)
	{
		return clazz.isInstance(thing) ? Optional.of((T) thing) : Optional.empty();
	}

}
