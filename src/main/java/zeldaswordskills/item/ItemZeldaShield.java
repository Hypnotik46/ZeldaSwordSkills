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

import mods.battlegear2.api.ISheathed;
import mods.battlegear2.api.core.InventoryPlayerBattle;
import mods.battlegear2.api.shield.IArrowCatcher;
import mods.battlegear2.api.shield.IArrowDisplay;
import mods.battlegear2.api.shield.IShield;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Icon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import zeldaswordskills.ZSSAchievements;
import zeldaswordskills.ZSSMain;
import zeldaswordskills.api.damage.DamageUtils.DamageSourceArmorBreak;
import zeldaswordskills.api.item.IDashItem;
import zeldaswordskills.api.item.IFairyUpgrade;
import zeldaswordskills.api.item.ISwingSpeed;
import zeldaswordskills.block.tileentity.TileEntityDungeonCore;
import zeldaswordskills.creativetab.ZSSCreativeTabs;
import zeldaswordskills.entity.ZSSPlayerInfo;
import zeldaswordskills.lib.ModInfo;
import zeldaswordskills.lib.Sounds;
import zeldaswordskills.util.PlayerUtils;
import zeldaswordskills.util.TargetUtils;
import zeldaswordskills.util.WorldUtils;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.Optional.Method;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Optional.InterfaceList(value={
		@Optional.Interface(iface="mods.battlegear2.api.ISheathed", modid="battlegear2", striprefs=true),
		@Optional.Interface(iface="mods.battlegear2.api.shield.IArrowCatcher", modid="battlegear2", striprefs=true),
		@Optional.Interface(iface="mods.battlegear2.api.shield.IArrowDisplay", modid="battlegear2", striprefs=true),
		@Optional.Interface(iface="mods.battlegear2.api.shield.IShield", modid="battlegear2", striprefs=true)
})
public class ItemZeldaShield extends Item implements IDashItem, IFairyUpgrade,
ISwingSpeed, IShield, ISheathed, IArrowCatcher, IArrowDisplay
{
	/** Time for which blocking will be disabled after a successful block */
	private final int recoveryTime;
	/** Rate at which BG2 stamina bar will decay per tick */
	private final float bg2DecayRate;
	/** Rate at which BG2 stamina bar will recover per tick; 0.012F takes 5 seconds */
	private final float bg2RecoveryRate;

	@SideOnly(Side.CLIENT)
	private Icon backIcon;

	/**
	 * @param recoveryTime time in ticks it takes to recover from a block when held normally
	 * @param decayRate number of seconds it will take the BG2 stamina bar to deplete
	 * @param recoveryRate number of seconds until BG2 stamina bar will completely replenish
	 */
	public ItemZeldaShield(int id, int recoveryTime, float decayRate, float recoveryRate) {
		super(id);
		this.recoveryTime = recoveryTime;
		this.bg2DecayRate = 1F / decayRate / 20F;
		this.bg2RecoveryRate = 1F / recoveryRate / 20F;
		setFull3D();
		setMaxDamage(64);
		setMaxStackSize(1);
		setCreativeTab(ZSSCreativeTabs.tabCombat);
	}

	/** Time for which blocking will be disabled after a successful block */
	public int getRecoveryTime() {
		return recoveryTime;
	}

	/**
	 * Returns true if the shield can block this kind of damage
	 */
	public boolean canBlockDamage(ItemStack shield, DamageSource source) {
		boolean flag = source.isUnblockable() && !(source instanceof DamageSourceArmorBreak);
		if (this == ZSSItems.shieldDeku) {
			return !flag;
		}
		return !flag || source.isMagicDamage() || source.isFireDamage() || (source.isProjectile() && this == ZSSItems.shieldMirror);
	}

	/**
	 * Called when the shield blocks an attack when held in the normal fashion (i.e. non-BG2)
	 * used by Deku Shield to damage / destroy the stack and by Mirror Shield to reflect projectiles
	 */
	public void onBlock(EntityPlayer player, ItemStack shield, DamageSource source, float damage) {
		ZSSPlayerInfo.get(player).onAttackBlocked(shield, damage);
		WorldUtils.playSoundAtEntity(player.worldObj, player, Sounds.HAMMER, 0.4F, 0.5F);
		if (this == ZSSItems.shieldDeku) {
			if (source.isProjectile() && source.getSourceOfDamage() instanceof IProjectile) {
                if (ZSSMain.isBG2Enabled && player.getHeldItem() == shield && shield.getItem() instanceof IArrowCatcher){
                    if (((IArrowCatcher) shield.getItem()).catchArrow(shield, player, (IProjectile) source.getSourceOfDamage())) {
                        ((InventoryPlayerBattle) player.inventory).hasChanged = true;
                    }
                }
            }
			int dmg = Math.round(source.isFireDamage() ? damage + 10.0F : damage - 2.0F);
			if (dmg > 0) {
				shield.damageItem(dmg, player);
				if (shield.stackSize <= 0) {
					player.destroyCurrentEquippedItem();
				}
			}
		} else if (this == ZSSItems.shieldMirror) {
			if (source.isProjectile() && !source.isExplosion() && source.getSourceOfDamage() != null) {
				if (player.worldObj.rand.nextFloat() < (source.isMagicDamage() ? (1F / 3F) : 1.0F)) {
					Entity projectile = null;
					try {
						projectile = source.getSourceOfDamage().getClass().getConstructor(World.class).newInstance(player.worldObj); 
					} catch (Exception e) {
						;
					}
					if (projectile != null) {
						NBTTagCompound data = new NBTTagCompound();
						source.getSourceOfDamage().writeToNBT(data);
						projectile.readFromNBT(data);
						projectile.posX -= projectile.motionX;
						projectile.posY -= projectile.motionY;
						projectile.posZ -= projectile.motionZ;
						double motionX = (double)(-MathHelper.sin(player.rotationYaw / 180.0F * (float) Math.PI) * MathHelper.cos(player.rotationPitch / 180.0F * (float) Math.PI));
						double motionZ = (double)(MathHelper.cos(player.rotationYaw / 180.0F * (float) Math.PI) * MathHelper.cos(player.rotationPitch / 180.0F * (float) Math.PI));
						double motionY = (double)(-MathHelper.sin(player.rotationPitch / 180.0F * (float) Math.PI));
						TargetUtils.setEntityHeading(projectile, motionX, motionY, motionZ, 1.0F, 2.0F + (20.0F * player.worldObj.rand.nextFloat()), false);
						player.worldObj.spawnEntityInWorld(projectile);
					}
				}
			}
		}
	}

	@Override
	public float getExhaustion() {
		return 0.3F;
	}

	@Override
	public int getSwingSpeed() {
		return 10; // same as BG2 bash speed
	}

	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return EnumAction.block;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 72000;
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity target) {
		if (target instanceof EntityLivingBase) {
			WorldUtils.playSoundAtEntity(player.worldObj, player, Sounds.HAMMER, 0.4F, 0.5F);
			TargetUtils.knockTargetBack((EntityLivingBase) target, player);
		}
		return true;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (ZSSPlayerInfo.get(player).canBlock()) {
			player.setItemInUse(stack, getMaxItemUseDuration(stack));
		}
		return stack;
	}

	@Override
	public void onUsingItemTick(ItemStack stack, EntityPlayer player, int count) {
		if (this == ZSSItems.shieldMirror) {
			if (player.getItemInUse() != null && ZSSPlayerInfo.get(player).canBlock()) {
				Vec3 vec3 = player.getLookVec();
				double dx = player.posX + vec3.xCoord * 2.0D;
				double dy = player.posY + player.getEyeHeight() + vec3.yCoord * 2.0D;
				double dz = player.posZ + vec3.zCoord * 2.0D;
				List<EntityFireball> list = player.worldObj.getEntitiesWithinAABB(EntityFireball.class,
						AxisAlignedBB.getBoundingBox(dx - 1, dy - 1, dz - 1, dx + 1, dy + 1, dz + 1));
				for (EntityFireball fireball : list) {
					DamageSource source = DamageSource.causeFireballDamage(fireball, fireball.shootingEntity);
					if (canBlockDamage(stack, source) && fireball.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F)) {
						ZSSPlayerInfo.get(player).onAttackBlocked(stack, 1.0F);
						WorldUtils.playSoundAtEntity(player.worldObj, player, Sounds.HAMMER, 0.4F, 0.5F);
						break;
					}
				}
			}
		}
	}

	@Override
	public int getItemEnchantability() {
		return 0;
	}

	@Override
	public boolean getIsRepairable(ItemStack toRepair, ItemStack stack) {
		return this == ZSSItems.shieldDeku && stack.getItem() == Item.itemsList[Block.planks.blockID];
	}

	@SideOnly(Side.CLIENT)
	public Icon getBackIcon() {
		return backIcon;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister register) {
		itemIcon = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9));
		backIcon = register.registerIcon(ModInfo.ID + ":" + getUnlocalizedName().substring(9) + "_back");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean isHeld) {
		list.add(EnumChatFormatting.ITALIC + StatCollector.translateToLocal("tooltip." + getUnlocalizedName().substring(5) + ".desc.0"));
	}

	@Override
	public void handleFairyUpgrade(EntityItem item, EntityPlayer player, TileEntityDungeonCore core) {
		if (PlayerUtils.hasItem(player, ZSSItems.swordMasterTrue)) {
			item.setDead();
			player.triggerAchievement(ZSSAchievements.shieldMirror);
			WorldUtils.spawnItemWithRandom(core.getWorldObj(), new ItemStack(ZSSItems.shieldMirror), core.xCoord, core.yCoord + 2, core.zCoord);
			core.getWorldObj().playSoundEffect(core.xCoord + 0.5D, core.yCoord + 1, core.zCoord + 0.5D, Sounds.SECRET_MEDLEY, 1.0F, 1.0F);
		} else {
			core.worldObj.playSoundEffect(core.xCoord + 0.5D, core.yCoord + 1, core.zCoord + 0.5D, Sounds.FAIRY_LAUGH, 1.0F, 1.0F);
			player.addChatMessage(StatCollector.translateToLocal("chat.zss.fairy.laugh.sword"));
		}
	}

	@Override
	public boolean hasFairyUpgrade(ItemStack stack) {
		return this == ZSSItems.shieldHylian;
	}

	@Method(modid="battlegear2")
	@Override
	public void setArrowCount(ItemStack stack, int count) {
		if (!stack.hasTagCompound()) { stack.setTagCompound(new NBTTagCompound()); }
		stack.getTagCompound().setShort("arrows", (short) Math.min(count, Short.MAX_VALUE));
	}

	@Method(modid="battlegear2")
	@Override
	public int getArrowCount(ItemStack stack) {
		return (stack.hasTagCompound() ? stack.getTagCompound().getShort("arrows") : 0);
	}

	@Method(modid="battlegear2")
	@Override
	public boolean catchArrow(ItemStack shield, EntityPlayer player, IProjectile projectile) {
		if (this == ZSSItems.shieldDeku && projectile instanceof EntityArrow){
			setArrowCount(shield, getArrowCount(shield) + 1);
			player.setArrowCountInEntity(player.getArrowCountInEntity() - 1);
			((EntityArrow) projectile).setDead();
			return true;
		}
		return false;
	}

	@Method(modid="battlegear2")
	@Override
	public boolean sheatheOnBack(ItemStack item) {
		return true;
	}

	@Method(modid="battlegear2")
	@Override
	public float getDecayRate(ItemStack shield) {
		return bg2DecayRate;
	}

	@Method(modid="battlegear2")
	@Override
	public float getRecoveryRate(ItemStack shield) {
		return bg2RecoveryRate;
	}

	@Method(modid="battlegear2")
	@Override
	public boolean canBlock(ItemStack shield, DamageSource source) {
		return canBlockDamage(shield, source);
	}

	@Method(modid="battlegear2")
	@Override
	public float getDamageDecayRate(ItemStack shield, float amount) {
		return 0.0F; // 1F/20F is the default BG2 value
	}

	@Method(modid="battlegear2")
	@Override
	public float getBlockAngle(ItemStack shield) {
		return 60; // this is the default BG2 value
	}

	@Method(modid="battlegear2")
	@Override
	public int getBashTimer(ItemStack shield) {
		return 10; // this is the default BG2 value
	}

	@Method(modid="battlegear2")
	@Override
	public void blockAnimation(EntityPlayer player, float amount) {
		// TODO BG2 sends a BattlegearShieldFlashPacket to all around; not sure what that does
		player.worldObj.playSoundAtEntity(player, "battlegear2:shield", 1, 1);
	}

	@Method(modid="battlegear2")
	@Override
	public float getDamageReduction(ItemStack shield, DamageSource source) {
		return (this == ZSSItems.shieldDeku ? (source.isFireDamage() ? -10.0F : 2.0F) : Float.MAX_VALUE);
	}
}
