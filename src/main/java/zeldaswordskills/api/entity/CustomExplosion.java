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

package zeldaswordskills.api.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.network.packet.Packet60Explosion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import zeldaswordskills.api.block.IExplodable;
import zeldaswordskills.block.ZSSBlocks;
import zeldaswordskills.entity.projectile.EntityBomb;
import zeldaswordskills.lib.Config;

/**
 * 
 * Custom Explosion class that allows for specifying whether or not to destroy blocks,
 * whether to inflict damage, how much and what type of damage to inflict, whether to
 * exclude liquids from the calculations, and also what particles to spawn under
 * various circumstances.
 * 
 * Extends vanilla Explosion class to allow substitution in several vanilla methods.
 * 
 * TODO allow setting custom particles to spawn
 * TODO allow setting custom sound filepath
 * 
 * @author coolAlias
 *
 */
public class CustomExplosion extends Explosion
{
	/**
	 * Creates an explosion based on the BombType given, automatically applying
	 * various characteristics. If more versatility than this is required, create a
	 * CustomExplosion object from scratch rather than using the static methods and
	 * call doExplosionA(), then doExplosionB().
	 */
	public static void createExplosion(World world, double x, double y, double z, float radius, BombType type) {
		createExplosion(new EntityBomb(world).setType(type), world, x, y, z, radius, 0.0F, true);
	}

	/**
	 * Creates an explosion based on the IEntityBomb given, automatically applying
	 * various characteristics. If more versatility than this is required, create a
	 * CustomExplosion object from scratch rather than using the static methods and
	 * call doExplosionA(), then doExplosionB().
	 * @param damage Use 0.0F for vanilla explosion damage; amounts above zero will cause a flat amount regardless of distance
	 */
	public static void createExplosion(IEntityBomb bomb, World world, double x, double y, double z, float radius, float damage, boolean canGrief) {
		CustomExplosion explosion = new CustomExplosion(world, (Entity) bomb, x, y, z, radius).setDamage(damage);
		BombType type = bomb.getType();
		// TODO Adventure Mode is only set per player, not per world
		//boolean isAdventureMode = (world.getWorldInfo().getGameType() == EnumGameType.ADVENTURE);
		boolean restrictBlocks = false;//(isAdventureMode && !bomb.canGriefAdventureMode());
		explosion.setMotionFactor(bomb.getMotionFactor());
		explosion.scalesWithDistance = (damage == 0.0F);
		explosion.isSmoking = canGrief;
		explosion.targetBlock = ((restrictBlocks || Config.onlyBombSecretStone()) ? ZSSBlocks.secretStone.blockID : -1);
		explosion.ignoreLiquids = (type != BombType.BOMB_STANDARD);
		explosion.ignoreLiquidType = (type == BombType.BOMB_FIRE ? 2 : (type == BombType.BOMB_WATER ? 1 : 0));
		float f = bomb.getDestructionFactor();
		if (world.provider.isHellWorld && type != BombType.BOMB_FIRE) {
			f *= 0.5F;
		}
		explosion.restrictExplosionBy(f);
		explosion.doExplosionA();
		explosion.doExplosionB(true);
	}

	/** Whether or not this explosion will damage entities within the blast explosionSize */
	public boolean inflictsDamage = true;

	/** Whether the damage amount scales with distance from explosion's center, as well as chance of catching fire */
	public boolean scalesWithDistance = true;

	/** Setting this to true will ignore liquids for the purpose of determining which blocks are destroyed */
	public boolean ignoreLiquids = false;

	/** Exclude only a certain type of liquid: 0 - all liquids; 1 - water only 2 - lava only*/
	public int ignoreLiquidType = 0;

	/** Specific block ID to target, if any (defaults to all blocks) */
	public int targetBlock = -1;

	/** Type of DamageSource that this explosion will cause; leave null for vanilla explosion damage */
	protected DamageSource source = null;

	/** Amount of damage to inflict; if 0.0F, vanilla explosion damage amount will be used instead */
	protected float damage = 0.0F;

	/** Amount of time for which affected entities will be set on fire; only if explosion isFlaming */
	protected int burnTime = 0;

	/** Factor by which affected entity's motion will be multiplied */
	protected float motionFactor = 1.0F;

	/** Maximum explosionSize within which blocks can be affected, regardless of explosion size */
	protected static final int MAX_RADIUS = 16;

	/** Restricts (or expands) the blast radius for destroying blocks by this factor */
	protected float restrictExplosion = 1.0F;

	/** Originally private Random from super class */
	protected final Random rand = new Random();

	/** Originally private World object from super class */
	protected final World worldObj;

	/** Affected players get added to this map; not sure what it's used for; private in super class */
	protected Map affectedPlayers = new HashMap();

	/**
	 * Creates a custom explosion; isSmoking is true by default, and isFlaming is false by default
	 */
	public CustomExplosion(World world, Entity exploder, double x, double y, double z, float explosionSize) {
		super(world, exploder, x, y, z, explosionSize);
		this.worldObj = world;
	}

	/**
	 * Sets the DamageSource this explosion will cause; returns itself for convenience
	 */
	public CustomExplosion setSource(DamageSource source) {
		this.source = source;
		return this;
	}

	/**
	 * Returns the damage source to use; if source is null, default explosion-type DamageSource is used
	 */
	protected DamageSource getDamageSource() {
		return (source != null ? source : DamageSource.setExplosionSource(this));
	}

	/**
	 * Sets amount of damage this explosion will cause; returns itself for convenience
	 */
	public CustomExplosion setDamage(float amount) {
		damage = amount;
		return this;
	}

	/**
	 * Sets amount of time for which affected entities will burn; returns itself for convenience
	 */
	public CustomExplosion setBurnTime(int ticks) {
		burnTime = ticks;
		return this;
	}

	/**
	 * Sets amount by which entity's motion will be multiplied
	 */
	public CustomExplosion setMotionFactor(float amount) {
		motionFactor = amount;
		return this;
	}

	/**
	 * Sets the factor by which to restrict block destruction (smaller factor means less destruction; 1.0F is normal size);
	 * returns itself for convenience
	 */
	public CustomExplosion restrictExplosionBy(float factor) {
		restrictExplosion = factor;
		return this;
	}

	/**
	 * Does the first part of the explosion (destroy blocks)
	 */
	@Override
	public void doExplosionA() {
		if (isSmoking && restrictExplosion > 0) {
			populateAffectedBlocksList();
		}
		if (inflictsDamage) {
			affectEntitiesWithin();
		}
	}

	/**
	 * Does the second part of the explosion (sound, particles, drop spawn)
	 */
	@Override
	public void doExplosionB(boolean spawnExtraParticles) {
		worldObj.playSoundEffect(explosionX, explosionY, explosionZ, "random.explode", 4.0F, (1.0F + (worldObj.rand.nextFloat() - worldObj.rand.nextFloat()) * 0.2F) * 0.7F);
		if (explosionSize >= 2.0F && isSmoking) {
			worldObj.spawnParticle("hugeexplosion", explosionX, explosionY, explosionZ, 1.0D, 0.0D, 0.0D);
		} else {
			worldObj.spawnParticle("largeexplode", explosionX, explosionY, explosionZ, 1.0D, 0.0D, 0.0D);
		}

		Iterator iterator;
		ChunkPosition chunkposition;
		int i, j, k, l;

		if (isSmoking) {
			iterator = affectedBlockPositions.iterator();
			while (iterator.hasNext()) {
				chunkposition = (ChunkPosition)iterator.next();
				i = chunkposition.x;
				j = chunkposition.y;
				k = chunkposition.z;
				l = worldObj.getBlockId(i, j, k);
				explodeBlockAt(l, i, j, k, spawnExtraParticles);
			}
		}

		if (isFlaming) {
			iterator = affectedBlockPositions.iterator();
			while (iterator.hasNext()) {
				chunkposition = (ChunkPosition)iterator.next();
				i = chunkposition.x;
				j = chunkposition.y;
				k = chunkposition.z;
				l = worldObj.getBlockId(i, j, k);
				int i1 = worldObj.getBlockId(i, j - 1, k);
				if (l == 0 && Block.opaqueCubeLookup[i1] && rand.nextInt(3) == 0) {
					worldObj.setBlock(i, j, k, Block.fire.blockID);
				}
			}
		}

		notifyClients();
	}

	/**
	 * Actually explodes the block and spawns particles if allowed
	 */
	private void explodeBlockAt(int blockId, int i, int j, int k, boolean spawnExtraParticles) {
		if (spawnExtraParticles) {
			double d0 = (double)((float) i + worldObj.rand.nextFloat());
			double d1 = (double)((float) j + worldObj.rand.nextFloat());
			double d2 = (double)((float) k + worldObj.rand.nextFloat());
			double d3 = d0 - explosionX;
			double d4 = d1 - explosionY;
			double d5 = d2 - explosionZ;
			double d6 = (double) MathHelper.sqrt_double(d3 * d3 + d4 * d4 + d5 * d5);
			d3 /= d6;
			d4 /= d6;
			d5 /= d6;
			double d7 = 0.5D / (d6 / (double) explosionSize + 0.1D);
			d7 *= (double)(worldObj.rand.nextFloat() * worldObj.rand.nextFloat() + 0.3F);
			d3 *= d7;
			d4 *= d7;
			d5 *= d7;
			worldObj.spawnParticle("explode", (d0 + explosionX * 1.0D) / 2.0D, (d1 + explosionY * 1.0D) / 2.0D, (d2 + explosionZ * 1.0D) / 2.0D, d3, d4, d5);
			worldObj.spawnParticle("smoke", d0, d1, d2, d3, d4, d5);
		}

		if (blockId > 0) {
			Block block = Block.blocksList[blockId];
			if (block.canDropFromExplosion(this)) {
				block.dropBlockAsItemWithChance(worldObj, i, j, k, worldObj.getBlockMetadata(i, j, k), 1.0F /  explosionSize, 0);
			}
			block.onBlockExploded(worldObj, i, j, k, this);
		}
	}

	/**
	 * Populates the affectedBlocksList with any blocks that should be affected by this explosion
	 */
	protected void populateAffectedBlocksList() {
		HashSet hashset = new HashSet();
		float radius = Math.min(explosionSize * restrictExplosion, 16.0F);
		for (int i = 0; i < MAX_RADIUS; ++i) {
			for (int j = 0; j < MAX_RADIUS; ++j) {
				for (int k = 0; k < MAX_RADIUS; ++k) {
					if (i == 0 || i == MAX_RADIUS - 1 || j == 0 || j == MAX_RADIUS - 1 || k == 0 || k == MAX_RADIUS - 1)
					{
						double d3 = (double)((float)i / ((float) MAX_RADIUS - 1.0F) * 2.0F - 1.0F);
						double d4 = (double)((float)j / ((float) MAX_RADIUS - 1.0F) * 2.0F - 1.0F);
						double d5 = (double)((float)k / ((float) MAX_RADIUS - 1.0F) * 2.0F - 1.0F);
						double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
						d3 /= d6;
						d4 /= d6;
						d5 /= d6;
						float f1 = radius * (0.7F + worldObj.rand.nextFloat() * 0.6F);
						double d0 = explosionX;
						double d1 = explosionY;
						double d2 = explosionZ;

						for (float f2 = 0.3F; f1 > 0.0F; f1 -= f2 * 0.75F)
						{
							int l = MathHelper.floor_double(d0);
							int i1 = MathHelper.floor_double(d1);
							int j1 = MathHelper.floor_double(d2);
							int k1 = worldObj.getBlockId(l, i1, j1);
							Block block = (k1 > 0 ? Block.blocksList[k1] : null);
							Block target = (targetBlock > 0 ? Block.blocksList[targetBlock] : null);

							if (k1 > 0) {
								boolean flag = !block.blockMaterial.isLiquid() || !ignoreLiquids || !(ignoreLiquidType != 0 &&
										((ignoreLiquidType == 1 && block.blockMaterial == Material.water) ||
												(ignoreLiquidType == 2 && block.blockMaterial == Material.lava)));
								if (flag) {
									float f3 = exploder != null ? exploder.getBlockExplosionResistance(this, worldObj, l, i1, j1, block) : block.getExplosionResistance(exploder, worldObj, l, i1, j1, explosionX, explosionY, explosionZ);
									f1 -= (f3 + 0.3F) * f2;
								}
							}

							if (f1 > 0.0F && (target == null || block == target || block instanceof IExplodable) &&
									(exploder == null || exploder.shouldExplodeBlock(this, worldObj, l, i1, j1, k1, f1)))
							{
								hashset.add(new ChunkPosition(l, i1, j1));
							}

							d0 += d3 * (double)f2;
							d1 += d4 * (double)f2;
							d2 += d5 * (double)f2;
						}
					}
				}
			}
		}

		affectedBlockPositions.addAll(hashset);
	}

	/**
	 * Affects all entities within the explosion, causing damage if flagged to do so
	 */
	protected void affectEntitiesWithin() {
		float diameter = explosionSize * 2.0F;
		int i = MathHelper.floor_double(explosionX - (double) explosionSize - 1.0D);
		int j = MathHelper.floor_double(explosionX + (double) explosionSize + 1.0D);
		int k = MathHelper.floor_double(explosionY - (double) explosionSize - 1.0D);
		int l1 = MathHelper.floor_double(explosionY + (double) explosionSize + 1.0D);
		int i2 = MathHelper.floor_double(explosionZ - (double) explosionSize - 1.0D);
		int j2 = MathHelper.floor_double(explosionZ + (double) explosionSize + 1.0D);
		List list = worldObj.getEntitiesWithinAABBExcludingEntity(exploder, AxisAlignedBB.getAABBPool().getAABB((double) i, (double) k, (double) i2, (double) j, (double) l1, (double) j2));
		Vec3 vec3 = worldObj.getWorldVec3Pool().getVecFromPool(explosionX, explosionY, explosionZ);

		for (int k2 = 0; k2 < list.size(); ++k2)
		{
			Entity entity = (Entity)list.get(k2);
			double d7 = (scalesWithDistance ? entity.getDistance(explosionX, explosionY, explosionZ) / (double) diameter : 0.0D);

			if (d7 <= 1.0D) {
				double d0 = entity.posX - explosionX;
				double d1 = entity.posY + (double) entity.getEyeHeight() - explosionY;
				double d2 = entity.posZ - explosionZ;
				double d8 = (double) MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);

				if (d8 != 0.0D) {
					d0 /= d8;
					d1 /= d8;
					d2 /= d8;
					double d9 = (double) worldObj.getBlockDensity(vec3, entity.boundingBox);
					double d10 = (1.0D - d7) * d9;
					float amount = (damage == 0.0F ? (float)((int)((d10 * d10 + d10) / 2.0D * 8.0D * diameter + 1.0D)) : damage * (float) d10);
					if (entity.attackEntityFrom(getDamageSource(), amount) && isFlaming && !entity.isImmuneToFire()) {
						if (!scalesWithDistance || rand.nextFloat() < d10) {
							entity.setFire(burnTime);
						}
					}

					double d11 = EnchantmentProtection.func_92092_a(entity, d10);
					entity.motionX += d0 * d11 * motionFactor;
					entity.motionY += d1 * d11 * motionFactor;
					entity.motionZ += d2 * d11 * motionFactor;

					if (entity instanceof EntityPlayer) {
						affectedPlayers.put((EntityPlayer) entity, worldObj.getWorldVec3Pool().getVecFromPool(d0 * d10, d1 * d10, d2 * d10));
					}
				}
			}
		}
	}

	/** Returns map of affected players */
	@Override
	public Map func_77277_b() { return affectedPlayers; }

	/**
	 * Returns either the entity that placed the explosive block, the entity that caused the explosion,
	 * the entity that threw the entity that caused the explosion, or null.
	 */
	@Override
	public EntityLivingBase getExplosivePlacedBy() {
		return exploder == null ? null : (exploder instanceof EntityTNTPrimed ? ((EntityTNTPrimed) exploder).getTntPlacedBy() : 
			(exploder instanceof EntityLivingBase ? (EntityLivingBase) exploder : (exploder instanceof EntityThrowable ? ((EntityThrowable) exploder).getThrower() : null)));
	}

	protected void notifyClients() {
		if (!worldObj.isRemote) {
			Iterator iterator = worldObj.playerEntities.iterator();
			while (iterator.hasNext()) {
				EntityPlayer entityplayer = (EntityPlayer)iterator.next();
				if (entityplayer.getDistanceSq(explosionX, explosionY, explosionZ) < 4096.0D) {
					((EntityPlayerMP)entityplayer).playerNetServerHandler.sendPacketToPlayer(new Packet60Explosion(explosionX, explosionY, explosionZ, explosionSize, affectedBlockPositions, (Vec3) this.func_77277_b().get(entityplayer)));
				}
			}
		}
	}
}
