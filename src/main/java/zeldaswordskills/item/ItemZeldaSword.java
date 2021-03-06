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

import mods.battlegear2.api.PlayerEventChild.OffhandAttackEvent;
import mods.battlegear2.api.weapons.IBattlegearWeapon;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Icon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import zeldaswordskills.ZSSAchievements;
import zeldaswordskills.api.item.IFairyUpgrade;
import zeldaswordskills.api.item.ISacredFlame;
import zeldaswordskills.api.item.ISwingSpeed;
import zeldaswordskills.block.BlockSacredFlame;
import zeldaswordskills.block.tileentity.TileEntityDungeonCore;
import zeldaswordskills.creativetab.ZSSCreativeTabs;
import zeldaswordskills.lib.Config;
import zeldaswordskills.lib.ModInfo;
import zeldaswordskills.lib.Sounds;
import zeldaswordskills.util.PlayerUtils;
import zeldaswordskills.util.WorldUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.Optional.Method;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * Base class for all ZSS Swords.
 * 
 * These require an anvil to be repaired, and if broken only a skilled blacksmith
 * is able to fix it.
 *
 */
@Optional.Interface(iface="mods.battlegear2.api.weapons.IBattlegearWeapon", modid="battlegear2", striprefs=true)
public class ItemZeldaSword extends ItemSword implements IBattlegearWeapon, IFairyUpgrade, ISacredFlame, ISwingSpeed
{
	/** Whether this sword requires two hands */
	protected final boolean twoHanded;

	/** Original ItemSword's field is private, but this has the same functionality */
	protected final float weaponDamage;

	/** Original ItemSword's field is private, so store tool material in case it's needed */
	protected final EnumToolMaterial toolMaterial;

	/** Whether this sword is considered a 'master' sword for purposes of skills and such*/
	protected boolean isMaster = false;

	/** Icon for the broken version of this sword */
	@SideOnly(Side.CLIENT)
	protected Icon brokenIcon;

	/** Whether this sword will give the 'broken' version when it breaks */
	protected boolean givesBrokenItem = true;

	public ItemZeldaSword(int id, EnumToolMaterial material, float bonusDamage) {
		this(id, material, bonusDamage, false);
	}

	public ItemZeldaSword(int id, EnumToolMaterial material, float bonusDamage, boolean twoHanded) {
		super(id, material);
		this.setNoRepair();
		this.toolMaterial = material;
		this.weaponDamage = 4.0F + bonusDamage + material.getDamageVsEntity();
		this.twoHanded = twoHanded;
		setCreativeTab(ZSSCreativeTabs.tabCombat);
	}

	/**
	 * Flags this sword as a 'master' sword, which also sets no item on break to true
	 */
	public ItemZeldaSword setMasterSword() {
		setNoItemOnBreak();
		isMaster = true;
		return this;
	}

	/** Whether this sword is considered a 'master' sword for purposes of skills and such*/
	public boolean isMasterSword() { return isMaster; }

	/**
	 * Sets this sword to not give the broken item version when the sword breaks
	 */
	public ItemZeldaSword setNoItemOnBreak() {
		givesBrokenItem = false;
		return this;
	}

	@Override
	public float getExhaustion() {
		return 0.0F;
	}

	@Override
	public int getSwingSpeed() {
		return twoHanded ? 15 : (isMaster ? 10 : 5);
	}

	@Override
	public int getItemEnchantability() {
		return (isMaster ? 0 : super.getItemEnchantability());
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
		stack.damageItem(1, attacker);
		onStackDamaged(stack, attacker);
		return true;
	}

	@Override
	public boolean onBlockDestroyed(ItemStack stack, World world, int blockID, int x, int y, int z, EntityLivingBase entity) {
		if ((double) Block.blocksList[blockID].getBlockHardness(world, x, y, z) != 0.0D) {
			stack.damageItem((stack.getItem() == ZSSItems.swordGiant ? stack.getMaxDamage() + 1 : 2), entity);
			onStackDamaged(stack, entity);
		}
		return true;
	}

	/**
	 * Called when the stack is damaged; if stack size is 0, gives appropriate broken sword item
	 */
	protected void onStackDamaged(ItemStack stack, EntityLivingBase entity) {
		if (stack.stackSize == 0 && givesBrokenItem && entity instanceof EntityPlayer) {
			PlayerUtils.addItemToInventory((EntityPlayer) entity, new ItemStack(ZSSItems.swordBroken, 1, stack.itemID));
		}
	}

	/**
	 * Override to add custom weapon damage field rather than vanilla ItemSword's field
	 */
	@Override
	public Multimap getItemAttributeModifiers() {
		Multimap multimap = HashMultimap.create();
		multimap.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Weapon modifier", (double) weaponDamage, 0));
		return multimap;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIconFromDamage(int par1) {
		return (par1 == -1 ? brokenIcon : itemIcon);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister register) {
		itemIcon = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9));
		if (givesBrokenItem) {
			brokenIcon = register.registerIcon(ModInfo.ID + ":broken_" + getUnlocalizedName().substring(9));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean isHeld) {
		list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip." + getUnlocalizedName().substring(5) + ".desc.0"));
		if (stack.getItem() == ZSSItems.swordTempered) {
			if (stack.hasTagCompound() && stack.getTagCompound().hasKey("zssHitCount")) {
				list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocalFormatted("tooltip.zss.sword_tempered.desc.1",stack.getTagCompound().getInteger("zssHitCount")));
			}
		} else if (stack.getItem() == ZSSItems.swordGolden) {
			if (stack.hasTagCompound() && stack.getTagCompound().hasKey("SacredFlames")) {
				int level = stack.getTagCompound().getInteger("SacredFlames");
				for (int i = 1; i < 5; ++i) {
					if (i != 3 && (level & i) != 0) {
						list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocalFormatted("tooltip.zss.sword_golden.desc.1", StatCollector.translateToLocal("misc.zss.sacred_flame.name." + i)));
					}
				}
			}
		}
	}

	@Override
	public void handleFairyUpgrade(EntityItem item, EntityPlayer player, TileEntityDungeonCore core) {
		ItemStack stack = item.getEntityItem();
		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("zssHitCount") && stack.getTagCompound().getInteger("zssHitCount") > Config.getRequiredKills()) {
			item.setDead();
			WorldUtils.spawnItemWithRandom(core.worldObj, new ItemStack(ZSSItems.swordGolden), core.xCoord, core.yCoord + 2, core.zCoord);
			core.worldObj.playSoundEffect(core.xCoord + 0.5D, core.yCoord + 1, core.zCoord + 0.5D, Sounds.FAIRY_BLESSING, 1.0F, 1.0F);
			player.addChatMessage(StatCollector.translateToLocal("chat.zss.sword.blessing"));
			player.triggerAchievement(ZSSAchievements.swordGolden);
		} else {
			core.worldObj.playSoundEffect(core.xCoord + 0.5D, core.yCoord + 1, core.zCoord + 0.5D, Sounds.FAIRY_LAUGH, 1.0F, 1.0F);
			player.addChatMessage(StatCollector.translateToLocal("chat.zss.fairy.laugh.unworthy"));
		}
	}

	@Override
	public boolean hasFairyUpgrade(ItemStack stack) {
		return this == ZSSItems.swordTempered;
	}

	/**
	 * Call when a player kills a mob with the Tempered Sword to increment the foes slain count
	 * There is no need to check if the held item is correct, as that is done here
	 */
	public static void onKilledMob(EntityPlayer player, IMob mob) {
		if (!player.worldObj.isRemote && player.getHeldItem() != null && player.getHeldItem().getItem() == ZSSItems.swordTempered) {
			ItemStack stack = player.getHeldItem();
			NBTTagCompound tag = stack.getTagCompound();
			if (tag == null) { tag = new NBTTagCompound(); }
			tag.setInteger("zssHitCount", tag.getInteger("zssHitCount") + 1);
			stack.setTagCompound(tag);
			if (tag.getInteger("zssHitCount") > Config.getRequiredKills()) {
				player.triggerAchievement(ZSSAchievements.swordEvil);
			}
		}
	}

	@Override
	public boolean onActivatedSacredFlame(ItemStack stack, World world, EntityPlayer player, int type, boolean isActive) {
		return false;
	}

	@Override
	public boolean onClickedSacredFlame(ItemStack stack, World world, EntityPlayer player, int type, boolean isActive) {
		if (world.isRemote) {
			return false;
		} else if (this == ZSSItems.swordGolden && isActive) {
			NBTTagCompound tag = stack.getTagCompound();
			if (tag == null) { tag = new NBTTagCompound(); }
			if ((tag.getInteger("SacredFlames") & type) == 0) {
				tag.setInteger("SacredFlames", tag.getInteger("SacredFlames") | type);
				stack.setTagCompound(tag);
				world.playSoundAtEntity(player, Sounds.FLAME_ABSORB, 1.0F, 1.0F);
				player.addChatMessage(StatCollector.translateToLocalFormatted("chat.zss.sacred_flame.new",
						getItemDisplayName(stack), StatCollector.translateToLocal("misc.zss.sacred_flame.name." + type)));
				player.triggerAchievement(ZSSAchievements.swordFlame);
				addSacredFlameEnchantments(stack, type);
				return true;
			} else {
				player.addChatMessage(StatCollector.translateToLocalFormatted("chat.zss.sacred_flame.old.same", getItemDisplayName(stack)));
			}
		} else {
			if (isActive) {
				player.addChatMessage(StatCollector.translateToLocal("chat.zss.sacred_flame.incorrect.sword"));
			} else {
				player.addChatMessage(StatCollector.translateToLocal("chat.zss.sacred_flame.inactive"));
			}
		}
		WorldUtils.playSoundAtEntity(world, player, Sounds.SWORD_MISS, 0.4F, 0.5F);
		return false;
	}

	/**
	 * Adds appropriate enchantments to Golden Sword when bathing in one of the Sacred Flames
	 * @param meta metadata value of the Sacred Flame
	 */
	private void addSacredFlameEnchantments(ItemStack stack, int type) {
		switch(type) {
		case BlockSacredFlame.DIN: stack.addEnchantment(Enchantment.fireAspect, 2); break;
		case BlockSacredFlame.FARORE: stack.addEnchantment(Enchantment.knockback, 2); break;
		case BlockSacredFlame.NAYRU: stack.addEnchantment(Enchantment.looting, 3); break;
		}
		boolean flag = false;
		NBTTagList enchList = (NBTTagList) stack.getTagCompound().getTag("ench");
		for (int i = 0; i < enchList.tagCount(); ++i) {
			NBTTagCompound compound = (NBTTagCompound) enchList.tagAt(i);
			if (compound.getShort("id") == Enchantment.sharpness.effectId) {
				short lvl = compound.getShort("lvl");
				if (lvl < Enchantment.sharpness.getMaxLevel()) {
					enchList.removeTag(i);
					stack.addEnchantment(Enchantment.sharpness, lvl + 1);
				}
				flag = true;
				break;
			}
		}
		if (!flag) {
			stack.addEnchantment(Enchantment.sharpness, 1);
		}
	}

	@Method(modid="battlegear2")
	@Override
	public boolean sheatheOnBack(ItemStack stack) {
		return true;
	}

	@Method(modid="battlegear2")
	@Override
	public boolean isOffhandHandDual(ItemStack stack) {
		return (Config.allowOffhandMaster() || !isMaster) && !twoHanded;
	}

	@Method(modid="battlegear2")
	@Override
	public boolean offhandAttackEntity(OffhandAttackEvent event, ItemStack main, ItemStack offhand) {
		return true;
	}

	@Method(modid="battlegear2")
	@Override
	public boolean offhandClickAir(PlayerInteractEvent event, ItemStack main, ItemStack offhand) {
		return true;
	}

	@Method(modid="battlegear2")
	@Override
	public boolean offhandClickBlock(PlayerInteractEvent event, ItemStack main, ItemStack offhand) {
		return true;
	}

	@Method(modid="battlegear2")
	@Override
	public void performPassiveEffects(Side side, ItemStack main, ItemStack offhand) {}

	@Method(modid="battlegear2")
	@Override
	public boolean allowOffhand(ItemStack main, ItemStack offhand) {
		return !twoHanded;// && (offhand == null || offhand.getItem() instanceof IShield);
	}
}
