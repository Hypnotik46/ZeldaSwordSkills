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

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Icon;
import net.minecraft.util.StatCollector;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import zeldaswordskills.api.entity.BombType;
import zeldaswordskills.creativetab.ZSSCreativeTabs;
import zeldaswordskills.entity.projectile.EntityBomb;
import zeldaswordskills.lib.ModInfo;
import zeldaswordskills.util.MerchantRecipeHelper;
import zeldaswordskills.util.WorldUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * A bag for holding bombs. The only means of obtaining this special bag is by finding it in
 * the world, or by purchasing one from a Priest for a hefty fee.
 * 
 * While held and so long as the bag is not full, any bombs in the player's inventory will be
 * stored automatically. Right-clicking will remove a bomb from the bag and place it in the
 * player's hands; 'b' can be pressed at any time to grab a bomb from the bag, even if it is
 * not currently being held.
 * 
 * If right-clicked while sneaking and there is another bomb bag with initial capacity
 * in the inventory that is either empty or contains the same type of bombs, the two
 * bags will combine resulting in a bag with increased carrying capacity.
 * 
 * Left-clicking on a ticking bomb with the bag will turn the bomb into an item that can be
 * picked up. Careful not to get blown up while trying!
 * 
 * NOTE: If in creative mode with full inventory and the player right-clicks while holding the
 * bag, it will simply be removed rather than dropping to the ground. This is vanilla behavior.
 * 
 */
public class ItemBombBag extends Item
{
	private static final int BASE_CAPACITY = 10, MAX_CAPACITY = 50;

	@SideOnly(Side.CLIENT)
	private Icon[] ones;

	@SideOnly(Side.CLIENT)
	private Icon[] tens;

	public ItemBombBag(int par1) {
		super(par1);
		setMaxDamage(0);
		setMaxStackSize(1);
		setCreativeTab(ZSSCreativeTabs.tabTools);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (!player.isSneaking() || !increaseCapacity(player, stack)) {
			if (player.capabilities.isCreativeMode || removeBomb(stack)) {
				if (!player.inventory.addItemStackToInventory(stack)) {
					player.dropPlayerItem(stack);
				}
				int type = getBagBombType(stack);
				return new ItemStack(ZSSItems.bomb, 1, (type > 0 ? type : 0));
			} else {
				return stack;
			}
		} else {
			return stack;
		}
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
		if (entity instanceof EntityBomb) {
			return ((EntityBomb) entity).disarm(entity.worldObj);
		} else if (entity instanceof EntityVillager && !player.worldObj.isRemote) {
			EntityVillager villager = (EntityVillager) entity;
			MerchantRecipeList trades = villager.getRecipes(player);
			if (villager.getProfession() != 1 && trades != null) {
				MerchantRecipe trade = new MerchantRecipe(stack.copy(), new ItemStack(Item.emerald, 16));
				if (player.worldObj.rand.nextFloat() < 0.2F && MerchantRecipeHelper.addToListWithCheck(trades, trade)) {
					player.addChatMessage(StatCollector.translateToLocal("chat.zss.trade.generic.sell.1"));
				} else {
					player.addChatMessage(StatCollector.translateToLocal("chat.zss.trade.generic.sorry.1"));
				}
			} else {
				player.addChatMessage(StatCollector.translateToLocal("chat.zss.trade.generic.sorry.0"));
			}
		}
		return true;
	}

	/**
	 * While held, the bomb bag will scan the player's inventory for bombs and attempt to store them
	 * @param slot inventory slot at which the item resides
	 * @param isHeld true if the item is currently held
	 */
	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
		if (isHeld && getBombsHeld(stack) < getCapacity(stack) && entity instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) entity;
			for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
				ItemStack invStack = player.inventory.getStackInSlot(i);
				if (invStack != null && areMatchingTypes(stack, invStack, true)) {
					if (addBombs(stack, invStack) < 1) {
						player.inventory.setInventorySlotContents(i, null);
						if (getBombsHeld(stack) == getCapacity(stack)) {
							return;
						}
					}
				}
			}
		}
	}

	@Override
	public Icon getIcon(ItemStack stack, int pass) {
		int bombsHeld = getBombsHeld(stack);
		switch(pass) {
		case 0: return itemIcon;
		case 1: return ones[bombsHeld % 10];
		case 2: return tens[(bombsHeld / 10) % 10];
		default: return itemIcon;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister register) {
		itemIcon = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9));
		ones = new Icon[10];
		tens = new Icon[10];
		for (int i = 0; i < 10; ++i) {
			ones[i] = register.registerIcon(ModInfo.ID + ":digits/" + (i == 0 ? "" : "00") + i);
			tens[i] = register.registerIcon(ModInfo.ID + ":digits/0" + i + (i == 0 ? "" : "0"));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean isHeld) {
		list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip.zss.bombbag.desc.0"));
		list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip.zss.bombbag.desc.1"));
		list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip.zss.bombbag.desc.2"));
		String bombName = StatCollector.translateToLocal((getBombsHeld(stack) > 0 && getBagBombType(stack) > 0) ?  "item.zss.bomb." + getBagBombType(stack) + ".name" : "item.zss.bomb.0.name");
		list.add(EnumChatFormatting.BOLD + StatCollector.translateToLocalFormatted("tooltip.zss.bombbag.desc.bombs", new Object[] {bombName,getBombsHeld(stack),getCapacity(stack)}));
	}
	
	/**
	 * Attempts to add an amount of bombs to the bag, returning any that wouldn't fit
	 * @return the number of bombs that wouldn't fit, if any (usually returns a negative value)
	 */
	private int addBombs(ItemStack stack, int amount) {
		int bombs = getBombsHeld(stack);
		if (bombs <= getCapacity(stack)) {
			stack.getTagCompound().setInteger("bombs", Math.min(bombs + amount, getCapacity(stack)));
		}
		return (amount - (getCapacity(stack) - bombs));
	}
	
	/**
	 * ItemStack sensitive version for setting bag's type when adding bombs
	 * @return the number of bombs that wouldn't fit, if any (usually returns a negative value)
	 */
	public int addBombs(ItemStack bag, ItemStack bombs) {
		if (areMatchingTypes(bag, bombs, true)) {
			int remaining = addBombs(bag, bombs.stackSize);
			setBagBombType(bag, ItemBomb.getType(bombs).ordinal());
			return remaining;
		} else {
			return bombs.stackSize;
		}
	}

	/**
	 * Returns true if a bomb was removed from the bag
	 */
	public boolean removeBomb(ItemStack stack) {
		if (getBombsHeld(stack) > 0) {
			addBombs(stack, -1);
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Empties the entire contents of the bomb bag into the player's inventory
	 * or onto the ground if there is no more room
	 */
	public void emptyBag(ItemStack stack, EntityPlayer player) {
		int n = getBombsHeld(stack);
		int type = getBagBombType(stack);
		if (type < 0 || n < 1) { return; }
		ItemStack newBag = new ItemStack(ZSSItems.bombBag);
		setCapacity(newBag, getCapacity(stack));
		
		if (player.inventory.addItemStackToInventory(newBag)) {
			player.setCurrentItemOrArmor(0, null);
			while (n-- > 0) {
				ItemStack bomb = new ItemStack(ZSSItems.bomb,1,type);
				if (!player.inventory.addItemStackToInventory(bomb)) {
					WorldUtils.spawnItemWithRandom(player.worldObj, bomb, (int) player.posX, (int) player.posY, (int) player.posZ);
				}
			}
		}
	}

	/**
	 * Returns max storage capacity for this bag
	 */
	public int getCapacity(ItemStack stack) {
		return getCapacity(stack, false);
	}
	
	/**
	 * Returns either the true NBT capacity for this bag, or the adjusted max capacity
	 */
	private int getCapacity(ItemStack stack, boolean trueCapacity) {
		verifyNBT(stack);
		int type = getBagBombType(stack);
		if (trueCapacity || type == -1 || type == BombType.BOMB_STANDARD.ordinal()) {
			return stack.getTagCompound().getInteger("capacity");
		} else {
			return stack.getTagCompound().getInteger("capacity") / 2;
		}
	}

	/**
	 * Set's the stacks capacity to 'size' or MAX_CAPACITY, whichever is smaller
	 */
	private void setCapacity(ItemStack stack, int size) {
		verifyNBT(stack);
		stack.getTagCompound().setInteger("capacity", Math.min(size, MAX_CAPACITY));
	}

	/**
	 * Returns number of bombs held in this bag
	 */
	public int getBombsHeld(ItemStack stack) {
		verifyNBT(stack);
		return stack.getTagCompound().getInteger("bombs");
	}
	
	/**
	 * Returns the ordinal value of the type of bomb held, or -1 if no current type
	 */
	public int getBagBombType(ItemStack bag) {
		verifyNBT(bag);
		return bag.getTagCompound().getInteger("type");
	}
	
	/**
	 * Sets the bags current type
	 */
	private void setBagBombType(ItemStack bag, int type) {
		verifyNBT(bag);
		bag.getTagCompound().setInteger("type", type);
	}
	
	/**
	 * Returns true if the stack is a bomb or a bomb bag and its type matches the
	 * type currently stored in the bag, or true if no bombs are currently stored
	 * @param isBomb true if searching for a bomb and not a bomb bag
	 */
	public boolean areMatchingTypes(ItemStack bag, ItemStack stack, boolean isBomb) {
		int type = getBagBombType(bag);
		if (isBomb && stack.getItem() instanceof ItemBomb) {
			return getBombsHeld(bag) == 0 || type == -1 || type == ItemBomb.getType(stack).ordinal();
		} else if (!isBomb && stack.getItem() instanceof ItemBombBag) {
			int type2 = getBagBombType(stack);
			return getBombsHeld(bag) == 0 || type == -1 || getBombsHeld(stack) == 0 || type == type2 || type2 == -1;
		} else {
			return false;
		}
	}

	/**
	 * Attempts to increase the capacity of the bomb bag by combining with another other bomb
	 * bag in the player's inventory, adding the contents as well
	 * @param player player using the itemstack
	 * @param stack itemstack that was used
	 * @return true if itemstack's capacity increased
	 */
	private boolean increaseCapacity(EntityPlayer player, ItemStack stack) {
		int capacity = getCapacity(stack, true);
		if (capacity < MAX_CAPACITY) {
			for (int i = 0; i < player.inventory.getSizeInventory(); ++i) {
				ItemStack invStack = player.inventory.getStackInSlot(i);
				if (invStack != null && invStack != stack && areMatchingTypes(stack, invStack, false)) {
					int newCapacity = capacity + getCapacity(invStack, true);
					if (newCapacity <= MAX_CAPACITY) {
						setCapacity(stack, newCapacity);
						addBombs(stack, getBombsHeld(invStack));
						if (getBagBombType(stack) == -1) {
							setBagBombType(stack, getBagBombType(invStack));
						}
						player.inventory.setInventorySlotContents(i, null);
						break;
					}
				}
			}
		}

		return getCapacity(stack, true) > capacity;
	}

	/**
	 * Ensures stack has correctly formatted NBT tag; if not, one is created
	 */
	private void verifyNBT(ItemStack stack) {
		if (!stack.hasTagCompound()) {
			stack.setTagCompound(new NBTTagCompound());
			stack.getTagCompound().setInteger("bombs", 0);
			stack.getTagCompound().setInteger("type", -1);
			stack.getTagCompound().setInteger("capacity", BASE_CAPACITY);
		}
	}
}
