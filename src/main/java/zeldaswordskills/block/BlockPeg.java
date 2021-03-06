/**
    Copyright (C) <2014> <coolAlias>

    This file is part of coolAlias' Zelda Sword Skills Minecraft Mod; as such,
    you can redistribute it and/or modify it under the terms of the GNU
    General Public License as published by the Free Software Foundation,
    either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package zeldaswordskills.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Icon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.Event.Result;
import zeldaswordskills.ZSSAchievements;
import zeldaswordskills.api.block.BlockWeight;
import zeldaswordskills.api.block.IHookable;
import zeldaswordskills.api.block.ISmashable;
import zeldaswordskills.api.item.HookshotType;
import zeldaswordskills.api.item.ISmashBlock;
import zeldaswordskills.creativetab.ZSSCreativeTabs;
import zeldaswordskills.lib.ModInfo;
import zeldaswordskills.lib.Sounds;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * A block that can be smashed into the ground and eventually broken.
 * Each smash increments the meta until it reaches MAX_STATE, after which
 * any blow that is at least a level higher than the weight can destroy it.
 * 
 * If not destroyed, the peg will eventually pop back up.
 *
 */
public class BlockPeg extends Block implements IDungeonBlock, IHookable, ISmashable
{
	/** The weight of this block, i.e. the difficulty of smashing this block */
	private final BlockWeight weight;
	/** Metadata value that signifies a fully smashed down peg */
	private static final int MAX_STATE = 3;

	public static final MaterialPeg pegWoodMaterial = new MaterialPeg(MapColor.woodColor);
	public static final MaterialPeg pegRustyMaterial = new MaterialPeg(MapColor.ironColor);

	@SideOnly(Side.CLIENT)
	private Icon iconTop;
	@SideOnly(Side.CLIENT)
	private Icon iconBottom;

	public BlockPeg(int id, Material material, BlockWeight weight) {
		super(id, material);
		this.weight = weight;
		disableStats();
		setTickRandomly(true);
		setBlockUnbreakable();
		setResistance(BlockWeight.IMPOSSIBLE.weight);
		setStepSound(soundStoneFootstep);
		setCreativeTab(ZSSCreativeTabs.tabBlocks);
		setBlockBounds(0.25F, 0.0F, 0.25F, 0.75F, 0.8F, 0.75F);
	}

	/** Returns appropriate sound based on block material */
	private String getHitSound() {
		return blockMaterial == Material.iron ? Sounds.HIT_RUSTY : Sounds.HIT_PEG;
	}

	@Override
	public boolean canAlwaysGrab(HookshotType type, World world, int x, int y, int z) {
		return type == HookshotType.MULTI_SHOT || type == HookshotType.MULTI_SHOT_EXT;
	}

	@Override
	public Result canDestroyBlock(HookshotType type, World world, int x, int y, int z) {
		return Result.DENY;
	}

	@Override
	public Material getHookableMaterial(HookshotType type, World world, int x, int y, int z) {
		return (blockMaterial == pegWoodMaterial ? Material.wood : Material.iron);
	}

	@Override
	public BlockWeight getSmashWeight(EntityPlayer player, ItemStack stack, int meta) {
		return weight;
	}

	@Override
	public Result onSmashed(World world, EntityPlayer player, ItemStack stack, int x, int y, int z, int side) {
		world.playSoundEffect(x, y, z, getHitSound(), (world.rand.nextFloat() * 0.4F + 0.5F), 1.0F / (world.rand.nextFloat() * 0.4F + 0.5F));
		if (side == 1) {
			int meta = world.getBlockMetadata(x, y, z);
			int impact = 1 + ((ISmashBlock) stack.getItem()).getSmashStrength(player, stack, this, meta).ordinal() - weight.ordinal();
			if (impact > 0) {
				boolean flag = meta < MAX_STATE;
				meta += impact;
				if (meta >= MAX_STATE) {
					if (this == ZSSBlocks.pegRusty) {
						player.triggerAchievement(ZSSAchievements.hardHitter);
					} else if (this == ZSSBlocks.pegWooden) {
						player.triggerAchievement(ZSSAchievements.hammerTime);
					}
				}
				if (meta > MAX_STATE && impact > 1) {
					flag = true;
					world.destroyBlock(x, y, z, false);
				} else {
					world.setBlockMetadataWithNotify(x, y, z, Math.min(meta, MAX_STATE), 3);
				}
				return (flag ? Result.ALLOW : Result.DENY);
			}
		}
		return Result.DENY;
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random rand) {
		int meta = world.getBlockMetadata(x, y, z);
		if (meta > 0) {
			world.setBlockMetadataWithNotify(x, y, z, meta - 1, 3);
		}
	}

	@Override
	public int tickRate(World world) {
		return 60;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean canEntityDestroy(World world, int x, int y, int z, Entity entity) {
		return false;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		int meta = world.getBlockMetadata(x, y, z);
		if (meta == 0) {
			return AxisAlignedBB.getAABBPool().getAABB(x + minX, y + minY, z + minZ, x + maxX, y + maxY + 0.5D, z + maxZ);
		} else if (meta >= MAX_STATE) {
			return null;
		} else {
			return super.getCollisionBoundingBoxFromPool(world, x, y, z);
		}
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
		int meta = Math.min(world.getBlockMetadata(x, y, z), MAX_STATE);
		setBlockBounds(0.25F, 0.0F, 0.25F, 0.75F, 0.8F - (meta * 0.2F), 0.75F);
	}

	@Override
	public void setBlockBoundsForItemRender() {
		setBlockBounds(0.25F, 0.0F, 0.25F, 0.75F, 0.8F, 0.75F);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int meta) {
		return (side == 0 ? iconBottom : side == 1 ? iconTop : blockIcon);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister register) {
		blockIcon = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9) + "_side");
		iconTop = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9) + "_top");
		iconBottom = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9) + "_bottom");
	}
}

class MaterialPeg extends Material {
	public MaterialPeg(MapColor color) {
		super(color);
		setRequiresTool();
		setImmovableMobility();
		setAdventureModeExempt();
	}
}
