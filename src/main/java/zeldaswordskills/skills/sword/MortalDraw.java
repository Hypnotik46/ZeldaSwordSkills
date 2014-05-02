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

package zeldaswordskills.skills.sword;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import zeldaswordskills.client.ZSSKeyHandler;
import zeldaswordskills.entity.ZSSPlayerInfo;
import zeldaswordskills.lib.Sounds;
import zeldaswordskills.network.MortalDrawPacket;
import zeldaswordskills.skills.ILockOnTarget;
import zeldaswordskills.skills.SkillActive;
import zeldaswordskills.util.PlayerUtils;
import zeldaswordskills.util.WorldUtils;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * MORTAL DRAW
 * Activation: While empty-handed and locked on, hold the block key and attack
 * Effect: The art of drawing the sword, or Battoujutsu, is a risky but deadly move, capable
 * of inflicting deadly wounds on unsuspecting opponents with a lightning-fast blade strike
 * Exhaustion: 3.0F - (0.2F * level)
 * Damage: If successful, inflicts double damage
 * Duration: Window of attack opportunity is (level + 2) ticks
 * Notes:
 * - The first sword found in the action bar will be used for the strike; plan accordingly
 * - There is a 1.5s cooldown between uses, representing re-sheathing of the sword
 *
 */
public class MortalDraw extends SkillActive
{
	/** Delay before skill can be used again */
	private static final int DELAY = 30;
	/** The time remaining during which the skill will succeed */
	private int attackTimer;
	/** Nearest sword slot index */
	private int swordSlot;

	public MortalDraw(String name) {
		super(name);
		setDisablesLMB();
	}

	private int getAttackTime() {
		return level + DELAY + 2;
	}

	private MortalDraw(MortalDraw skill) {
		super(skill);
	}

	@Override
	public MortalDraw newInstance() {
		return new MortalDraw(this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public List<String> getDescription(EntityPlayer player) {
		List<String> desc = getDescription();
		desc.add(StatCollector.translateToLocalFormatted(getUnlocalizedDescription(3),
				(getAttackTime() - DELAY)));
		desc.add(getExhaustionDisplay(getExhaustion()));
		return desc;
	}

	@Override
	public boolean canDrop() {
		return false;
	}

	@Override
	public boolean isLoot() {
		return false;
	}

	@Override
	public boolean isActive() {
		return attackTimer > DELAY;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean canExecute(EntityPlayer player) {
		return player.getHeldItem() == null && (Minecraft.getMinecraft().gameSettings.keyBindUseItem.pressed
				|| ZSSKeyHandler.keys[ZSSKeyHandler.KEY_BLOCK].pressed);
	}

	@Override
	public boolean canUse(EntityPlayer player) {
		return super.canUse(player) && attackTimer == 0 && swordSlot > -1 && player.getHeldItem() == null;
	}

	@Override
	protected float getExhaustion() {
		return 3.0F - (0.2F * level);
	}

	@Override
	public boolean activate(World world, EntityPlayer player) {
		if (attackTimer == 0) {
			swordSlot = -1;
			for (int i = 0; i < 9; ++i) {
				ItemStack stack = player.inventory.getStackInSlot(i);
				if (stack != null && PlayerUtils.isSwordItem(stack.getItem())) {
					swordSlot = i;
					break;
				}
			}
		}
		if (super.activate(world, player)) {
			attackTimer = getAttackTime();
		}
		return isActive();
	}

	@Override
	public void onUpdate(EntityPlayer player) {
		if (attackTimer > 0) {
			--attackTimer;
			if (attackTimer == DELAY && !player.worldObj.isRemote) {
				drawSword(player, null);
				if (player.getHeldItem() != null) {
					PacketDispatcher.sendPacketToPlayer(new MortalDrawPacket().makePacket(), (Player) player);
				}
			}
		}
	}

	/**
	 * Returns true if the player was able to draw a sword
	 */
	public boolean drawSword(EntityPlayer player, Entity attacker) {
		boolean flag = false;
		if (swordSlot > -1 && swordSlot != player.inventory.currentItem) {
			player.setCurrentItemOrArmor(0, player.inventory.getStackInSlot(swordSlot));
			if (!player.worldObj.isRemote) { // only care about this on server:
				player.inventory.setInventorySlotContents(swordSlot, null);
				ILockOnTarget skill = ZSSPlayerInfo.get(player).getTargetingSkill();
				flag = (skill != null && skill.getCurrentTarget() == attacker);
			}
		}
		swordSlot = -1;
		return flag;
	}

	/**
	 * Call upon landing a mortal draw blow
	 */
	public void onImpact(EntityPlayer player, LivingHurtEvent event) {
		attackTimer = DELAY;
		event.ammount *= 2.0F;
		WorldUtils.playSoundAtEntity(player.worldObj, player, Sounds.MORTAL_DRAW, 0.4F, 0.5F);
	}
}
