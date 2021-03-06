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

package zeldaswordskills.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityOwnable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;


/**
 * 
 * A collection of methods related to target acquisition
 *
 */
public class TargetUtils
{
	/** Maximum range within which to search for targets */
	private static final int MAX_DISTANCE = 256;
	/** Max distance squared, used for comparing target distances (avoids having to call sqrt) */
	private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;
	
	// TODO write general MovingObjectPosition method, then have specific methods return blockHit or entityHit from that
	// TODO methods for acquiring multiple targets (beam, sphere, etc) with optional number of targets to acquire
	
	/** Returns the player's current reach distance, taking held item into account if applicable */
	// Packet7UseEntity uses 36.0D for determining if an attack should hit, or 9.0D if the entity cannot be seen
	// EntityRenderer uses 36.0D for creative mode, otherwise 9.0D, in calculating whether mouseover entity should be null
	// but using this exactly results in some attacks that in reality hit, being counted as a miss
	// Unlike Creative Mode, the mouseover is always null when an attack should miss when in Survival
	public static double getReachDistanceSq(EntityPlayer player) {
		return 38.5D; // seems to be just about right for Creative Mode hit detection
	}
	
	/**
	 * Returns true if current target is within the player's reach distance; does NOT check mouse over
	 */
	public static boolean canReachTarget(EntityPlayer player, Entity target) {
		return (player.canEntityBeSeen(target) && player.getDistanceSqToEntity(target) < getReachDistanceSq(player));
	}
	
	/**
	 * Returns true if the entity is directly in the crosshairs
	 */
	@SideOnly(Side.CLIENT)
	public static boolean isMouseOverEntity(Entity entity) {
		MovingObjectPosition mop = Minecraft.getMinecraft().objectMouseOver;
		return (mop != null && mop.entityHit == entity);
	}
	
	/**
	 * Returns the Entity that the mouse is currently over, or null
	 */
	@SideOnly(Side.CLIENT)
	public static Entity getMouseOverEntity() {
		MovingObjectPosition mop = Minecraft.getMinecraft().objectMouseOver;
		return (mop == null ? null : mop.entityHit);
	}
	
	/** Returns the EntityLivingBase closest to the point at which the seeker is looking and within the distance and radius specified */
	public static final EntityLivingBase acquireLookTarget(EntityLivingBase seeker, int distance, double radius) {
		return acquireLookTarget(seeker, distance, radius, false);
	}
	
	/**
	 * Returns the EntityLivingBase closest to the point at which the entity is looking and within the distance and radius specified
	 * @param distance max distance to check for target, in blocks; negative value will check to MAX_DISTANCE
	 * @param radius max distance, in blocks, to search on either side of the vector's path
	 * @param closestToEntity if true, the target closest to the seeker and still within the line of sight search radius is returned
	 * @return the entity the seeker is looking at or null if no entity within sight search range
	 */
	public static final EntityLivingBase acquireLookTarget(EntityLivingBase seeker, int distance, double radius, boolean closestToSeeker) {
		if (distance < 0 || distance > MAX_DISTANCE) {
			distance = MAX_DISTANCE;
		}
		EntityLivingBase currentTarget = null;
		double currentDistance = MAX_DISTANCE_SQ;
		Vec3 vec3 = seeker.getLookVec();
		double targetX = seeker.posX;
		double targetY = seeker.posY + seeker.getEyeHeight() - 0.10000000149011612D;
		double targetZ = seeker.posZ;
		double distanceTraveled = 0;
		
		while ((int) distanceTraveled < distance) {
			targetX += vec3.xCoord;
			targetY += vec3.yCoord;
			targetZ += vec3.zCoord;
			distanceTraveled += vec3.lengthVector();
			
			List<EntityLivingBase> list = seeker.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
				AxisAlignedBB.getBoundingBox(targetX-radius, targetY-radius, targetZ-radius, targetX+radius, targetY+radius, targetZ+radius));
			for (EntityLivingBase target : list) {
				if (target != seeker && target.canBeCollidedWith() && isTargetInSight(vec3, seeker, target)) {
					double newDistance = (closestToSeeker ? target.getDistanceSqToEntity(seeker) : target.getDistanceSq(targetX, targetY, targetZ));
					if (newDistance < currentDistance) {
						currentTarget = target;
						currentDistance = newDistance;
					}
				}
			}
		}
		
		return currentTarget;
	}
	
	/**
	 * Similar to the single entity version, but this method returns a List of all EntityLivingBase entities
	 * that are within the entity's field of vision, up to a certain range and distance away
	 */
	public static final List<EntityLivingBase> acquireAllLookTargets(EntityLivingBase seeker, int distance, double radius) {
		if (distance < 0 || distance > MAX_DISTANCE) {
			distance = MAX_DISTANCE;
		}
		List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
		Vec3 vec3 = seeker.getLookVec();
		double targetX = seeker.posX;
		double targetY = seeker.posY + seeker.getEyeHeight() - 0.10000000149011612D;
		double targetZ = seeker.posZ;
		double distanceTraveled = 0;

		while ((int) distanceTraveled < distance) {
			targetX += vec3.xCoord;
			targetY += vec3.yCoord;
			targetZ += vec3.zCoord;
			distanceTraveled += vec3.lengthVector();
			List<EntityLivingBase> list = seeker.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
				AxisAlignedBB.getBoundingBox(targetX-radius, targetY-radius, targetZ-radius, targetX+radius, targetY+radius, targetZ+radius));
			for (EntityLivingBase target : list) {
				if (target != seeker && target.canBeCollidedWith() && isTargetInSight(vec3, seeker, target)) {
					if (!targets.contains(target)) {
						targets.add(target);
					}
				}
			}
		}
		
		return targets;
	}
	
	/**
	 * Returns whether the target is in the seeker's field of view based on relative position
	 * @param fov seeker's field of view; a wider angle returns true more often
	 */
	public static final boolean isTargetInFrontOf(Entity seeker, Entity target, float fov) {
		double dx = target.posX - seeker.posX;
		double dz;
		for (dz = target.posZ - seeker.posZ; dx * dx + dz * dz < 1.0E-4D; dz = (Math.random() - Math.random()) * 0.01D) {
			dx = (Math.random() - Math.random()) * 0.01D;
		}
		float yaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - seeker.rotationYaw;
		yaw = yaw - 90;
		while (yaw < -180) { yaw += 360; }
		while (yaw >= 180) { yaw -= 360; }
		return yaw < fov && yaw > -fov;
	}
	
	/**
	 * Returns true if the target's position is within the area that the seeker is facing and the target can be seen
	 */
	public static final boolean isTargetInSight(EntityLivingBase seeker, Entity target) {
		return isTargetInSight(seeker.getLookVec(), seeker, target);
	}
	
	/**
	 * Returns true if the target's position is within the area that the seeker is facing and the target can be seen
	 */
	private static final boolean isTargetInSight(Vec3 vec3, EntityLivingBase seeker, Entity target) {
		return seeker.canEntityBeSeen(target) && isTargetInFrontOf(seeker, target, 60);
	}
	
	/**
	 * Applies all vanilla modifiers to passed in arrow (e.g. enchantment bonuses, critical, etc)
	 * @param charge should be a value between 0.0F and 1.0F, inclusive
	 */
	public static final void applyArrowSettings(EntityArrow arrow, ItemStack bow, float charge) {
		if (charge < 0.0F) { charge = 0.0F; }
		if (charge > 1.0F) { charge = 1.0F; }
		
		if (charge == 1.0F) { arrow.setIsCritical(true); }

		int k = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, bow);

		if (k > 0) { arrow.setDamage(arrow.getDamage() + (double) k * 0.5D + 0.5D); }

		int l = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, bow);

		if (l > 0) { arrow.setKnockbackStrength(l); }

		if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, bow) > 0) {
			arrow.setFire(100);
		}
	}

	/**
	 * Sets an entity's motion along the given vector at the given velocity, with wobble being
	 * an amount of variation applied to the course.
	 * @param wobble set to 0.0F for a true heading
	 * @param backwards if true, will set the entity's rotation to the opposite direction
	 */
	public static void setEntityHeading(Entity entity, double vecX, double vecY, double vecZ, float velocity, float wobble, boolean backwards) {
		float vectorLength = MathHelper.sqrt_double(vecX * vecX + vecY * vecY + vecZ * vecZ);
		vecX /= vectorLength;
		vecY /= vectorLength;
		vecZ /= vectorLength;
		vecX += entity.worldObj.rand.nextGaussian() * (entity.worldObj.rand.nextBoolean() ? -1 : 1) * 0.007499999832361937D * wobble;
		vecY += entity.worldObj.rand.nextGaussian() * (entity.worldObj.rand.nextBoolean() ? -1 : 1) * 0.007499999832361937D * wobble;
		vecZ += entity.worldObj.rand.nextGaussian() * (entity.worldObj.rand.nextBoolean() ? -1 : 1) * 0.007499999832361937D * wobble;
		vecX *= velocity;
		vecY *= velocity;
		vecZ *= velocity;
		entity.motionX = vecX;
		entity.motionY = vecY;
		entity.motionZ = vecZ;
		float f = MathHelper.sqrt_double(vecX * vecX + vecZ * vecZ);
		entity.prevRotationYaw = entity.rotationYaw = (backwards ? -1 : 1) * (float)(Math.atan2(vecX, vecZ) * 180.0D / Math.PI);
		entity.prevRotationPitch = entity.rotationPitch = (backwards ? -1 : 1) * (float)(Math.atan2(vecY, f) * 180.0D / Math.PI);
	}
	
	/**
	 * Returns true if the entity is considered friendly to the player (or IS the player)
	 */
	public static boolean isOnTeam(EntityPlayer player, EntityLivingBase entity) {
		if (entity == player) {
			return true;
		} else if (player.isOnSameTeam(entity)) {
			return true;
		} else if (entity instanceof EntityOwnable) {
			return ((EntityOwnable) entity).getOwnerName().equals(player.username);
		} else {
			return false;
		}
	}
	
	/**
	 * Returns true if the entity has an unimpeded view of the sky
	 */
	public static boolean canEntitySeeSky(World world, Entity entity) {
		int x = MathHelper.floor_double(entity.posX);
		int z = MathHelper.floor_double(entity.posZ);
		for (int y = MathHelper.floor_double(entity.posY) + 1; y < world.getActualHeight(); ++y) {
			if (!world.isAirBlock(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Knocks the pushed entity back slightly as though struck by the pushing entity
	 */
	public static final void knockTargetBack(EntityLivingBase pushedEntity, EntityLivingBase pushingEntity) {
		if (pushedEntity.canBePushed()) {
			double dx = pushedEntity.posX - pushingEntity.posX;
			double dz;
			for (dz = pushedEntity.posZ - pushingEntity.posZ; dx * dx + dz * dz < 1.0E-4D; dz = (Math.random() - Math.random()) * 0.01D){
				dx = (Math.random() - Math.random()) * 0.01D;
			}
			pushedEntity.knockBack(pushingEntity, 0, -dx, -dz);
		}
	}
}
