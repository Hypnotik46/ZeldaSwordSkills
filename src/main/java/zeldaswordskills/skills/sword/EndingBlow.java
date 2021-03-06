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

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import zeldaswordskills.entity.ZSSEntityInfo;
import zeldaswordskills.entity.ZSSPlayerInfo;
import zeldaswordskills.entity.buff.Buff;
import zeldaswordskills.lib.Sounds;
import zeldaswordskills.skills.ICombo;
import zeldaswordskills.skills.ILockOnTarget;
import zeldaswordskills.skills.SkillActive;
import zeldaswordskills.util.PlayerUtils;
import zeldaswordskills.util.WorldUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * ENDING BLOW
 * Description: Finish off an enemy made vulnerable by your flurry of blows
 * Activation: Forward, forward, and attack during combo
 * Effect:	Build up combo momentum and then finish off your enemy with a decisive strike,
 * 			gaining bonus xp if successful or becoming flat-footed if not
 * Damage: +(level * 20) percent
 * Duration of vulnerability: 110 - (level * 10) ticks
 * Exhaustion: 2.0F - (level * 0.1F)
 * XP Bonus: level + (value between 1 and the opponent's last remaining health)
 * Special:
 * - May only be used after two or more consecutive strikes on the same target
 * - Slaying an opponent with this move grants additional experience
 * - Failure to slay the target results in a -50% defense penalty for the duration
 * 
 */
public class EndingBlow extends SkillActive
{
	/** Set to 1 when activated; set to 0 when target struck in onImpact() */
	private int activeTimer = 0;
	/** Only for vanilla activation: Current number of ticks remaining before skill will not activate */
	@SideOnly(Side.CLIENT)
	private int ticksTilFail;
	/** Number of times the forward key has been pressed this activation cycle */
	@SideOnly(Side.CLIENT)
	private int keyPressed;
	/** Number of consecutive hits the combo had when the skill was last used */
	private int lastNumHits;
	/** Workaround for armor / potions changing damage: checks next tick if entity is dead or not */
	private EntityLivingBase entityHit;
	/** Xp amount to grant if entityHit is dead on update tick */
	private int xp;

	public EndingBlow(String name) {
		super(name);
	}

	private EndingBlow(EndingBlow skill) {
		super(skill);
	}

	@Override
	public EndingBlow newInstance() {
		return new EndingBlow(this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(List<String> desc, EntityPlayer player) {
		desc.add(getDamageDisplay(level * 20, true) + "%");
		desc.add(getDurationDisplay(getDuration(), true));
		desc.add(getExhaustionDisplay(getExhaustion()));
	}

	@Override
	public boolean isActive() {
		return activeTimer > 0;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean canExecute(EntityPlayer player) {
		return keyPressed > 1;
	}

	@Override
	public boolean canUse(EntityPlayer player) {
		if (super.canUse(player) && !isActive() && PlayerUtils.isHoldingSkillItem(player)) {
			ICombo skill = ZSSPlayerInfo.get(player).getComboSkill();
			if (skill != null && skill.isComboInProgress()) {
				if (lastNumHits > 0) {
					return skill.getCombo().getConsecutiveHits() > 1 && skill.getCombo().getSize() > lastNumHits + 2;
				} else {
					return skill.getCombo().getConsecutiveHits() > 1;
				}
			}
		}
		return false;
	}

	@Override
	protected float getExhaustion() {
		return 2.0F - (level * 0.1F);
	}

	/** Returns the duration of the defense down effect */
	public int getDuration() {
		return 110 - (level * 10);
	}

	@Override
	public boolean activate(World world, EntityPlayer player) {
		if (super.activate(world, player)) {
			activeTimer = 1;
			ICombo skill = ZSSPlayerInfo.get(player).getComboSkill();
			if (skill.getCombo() != null) {
				lastNumHits = skill.getCombo().getSize();
			}
			if (world.isRemote) {
				keyPressed = 0;
			}
		}
		return isActive();
	}

	@Override
	public void onUpdate(EntityPlayer player) {
		if (player.worldObj.isRemote && ticksTilFail > 0) {
			--ticksTilFail;
			if (ticksTilFail == 0) {
				keyPressed = 0;
			}
		}
		if (lastNumHits > 0) {
			// only ever true on server, which is fine:
			if (entityHit != null && xp > 0) {
				updateEntityState(player);
			}
			ICombo skill = ZSSPlayerInfo.get(player).getComboSkill();
			if (skill == null || !skill.isComboInProgress()) {
				lastNumHits = 0;
			}
		}
		if (isActive()) {
			activeTimer = 0;
			if (!player.worldObj.isRemote) {
				ZSSEntityInfo.get(player).applyBuff(Buff.DEFENSE_DOWN, getDuration() * 2, 50);
			}
		}
	}

	/**
	 * Checks if entity hit is dead, granting Xp or causing defensive penalty
	 */
	private void updateEntityState(EntityPlayer player) {
		if (entityHit.getHealth() <= 0.0F) {
			if (entityHit instanceof EntityLiving) {
				((EntityLiving) entityHit).experienceValue += xp;
			} else {
				WorldUtils.spawnXPOrbsWithRandom(player.worldObj, player.worldObj.rand, MathHelper.floor_double(entityHit.posX),
						MathHelper.floor_double(entityHit.posY), MathHelper.floor_double(entityHit.posZ), xp);
			}
		} else {
			WorldUtils.playSoundAtEntity(player.worldObj, player, Sounds.GRUNT, 0.3F, 0.8F);
			ZSSEntityInfo.get(player).applyBuff(Buff.DEFENSE_DOWN, getDuration(), 50);
		}
		entityHit = null;
		xp = 0;
	}

	/**
	 * Call upon landing a blow to increase the damage
	 */
	public void onImpact(EntityPlayer player, LivingHurtEvent event) {
		activeTimer = 0;
		ICombo combo = ZSSPlayerInfo.get(player).getComboSkill();
		ILockOnTarget lock = ZSSPlayerInfo.get(player).getTargetingSkill();
		if (combo != null && combo.isComboInProgress() && lock != null && lock.getCurrentTarget() == combo.getCombo().getLastEntityHit()) {
			event.ammount *= 1.0F + (level * 0.2F);
			WorldUtils.playSoundAtEntity(player.worldObj, player, Sounds.MORTAL_DRAW, 0.4F, 0.5F);
			entityHit = event.entityLiving;
			xp = level + 1 + player.worldObj.rand.nextInt(Math.max(2, MathHelper.ceiling_float_int(event.entityLiving.getHealth())));
		}
	}

	/**
	 * Increments the number of times the key has been pressed and starts the fail timer if not yet set
	 */
	@SideOnly(Side.CLIENT)
	public void keyPressed() {
		if (ticksTilFail == 0) {
			ticksTilFail = 6;
		}
		++keyPressed;
	}
}
