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

package zeldaswordskills.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlockWithMetadata;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Icon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import zeldaswordskills.block.BlockSecretStone;
import zeldaswordskills.block.ZSSBlocks;
import zeldaswordskills.block.tileentity.TileEntityDungeonBlock;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * ItemBlock for any block that provides TileEntityDungeonBlock.
 * 
 * Right-clicking any simple block while sneaking will alter the texture
 * of the itemblock to match, both while held and when placed. 
 *
 */
public class ItemDungeonBlock extends ItemBlockWithMetadata {

	public ItemDungeonBlock(int id, Block block) {
		super(id, block);
	}

	@Override
	public String getItemDisplayName(ItemStack stack) {
		return StatCollector.translateToLocal(getUnlocalizedName() + (stack.getItemDamage() > 7 ? ".name.unbreakable" : ".name"));
	}

	/**
	 * Returns the block that will be placed into the world; also used for rendering
	 */
	public Block getBlockFromStack(ItemStack stack) {
		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("renderBlock")) {
			int blockID = stack.getTagCompound().getInteger("renderBlock");
			return Block.blocksList[blockID];
		}
		return Block.blocksList[getBlockID()];
	}

	/** Returns the metadata value associated with the block to place or render */
	public int getMetaFromStack(ItemStack stack) {
		return (stack.hasTagCompound() ? stack.getTagCompound().getInteger("metadata") : 0);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIcon(ItemStack stack, int pass) {
		Block block = getBlockFromStack(stack);
		return (block != null && block.blockID != getBlockID() ? block.getIcon(1, getMetaFromStack(stack)) : Block.stone.getIcon(1, 0));
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		if (player.isSneaking()) {
			if (!world.isRemote) {
				int blockID = world.getBlockId(x, y, z);
				int meta = world.getBlockMetadata(x, y, z);
				Block block = (blockID > 0 ? Block.blocksList[blockID] : null);
				if (block == ZSSBlocks.secretStone) {
					block = Block.blocksList[BlockSecretStone.getIdFromMeta(meta)];
					meta = 0;
				} else {
					TileEntity te = world.getBlockTileEntity(x, y, z);
					if (te instanceof TileEntityDungeonBlock) {
						block = ((TileEntityDungeonBlock) te).getRenderBlock();
						meta = ((TileEntityDungeonBlock) te).getRenderMetadata();
					}
				}
				if (block != null && (block.isOpaqueCube() || block == Block.ice)) {
					if (!stack.hasTagCompound()) {
						stack.setTagCompound(new NBTTagCompound());
					}
					stack.getTagCompound().setInteger("renderBlock", block.blockID);
					stack.getTagCompound().setInteger("metadata", meta);
				}
			}
			return false;
		} else {
			return super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack,	EntityPlayer player, List list, boolean isHeld) {
		if (stack.getItemDamage() > 7) {
			list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip.zss.block.unbreakable.desc"));
		} else {
			list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip.zss.block." +
					(getBlockID() == ZSSBlocks.dungeonCore.blockID ? "core" : "dungeon") + ".desc.0"));
		}
		list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip.zss.block.dungeon.desc.1"));
	}
}
