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

package zeldaswordskills.client.render.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 
 * Renderer for generic living entities whose animations are handled via the model
 * class methods {@link ModelBase#setRotationAngles(float, float, float, float, float, float, Entity) setRotationAngles}
 * and {@link ModelBase#setLivingAnimations(EntityLivingBase, float, float, float) setLivingAnimations}.
 * 
 * If the entity has a child version, it will be rendered at half the adult scale.
 *
 */
@SideOnly(Side.CLIENT)
public class RenderGenericLiving extends RenderLiving
{
	private final ResourceLocation texture;
	private final float scale;

	/**
	 * @param model			Any animations need to be handled in the model class directly
	 * @param scale			Scale of the full size model; child versions will render at half this scale
	 * @param texturePath	Be sure to prefix with the Mod ID if needed, otherwise it will use the Minecraft texture path
	 */
	public RenderGenericLiving(ModelBase model, float shadowSize, float scale, String texturePath) {
		super(model, shadowSize);
		this.texture = new ResourceLocation(texturePath);
		this.scale = scale;
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		return texture;
	}

	@Override
	protected void preRenderCallback(EntityLivingBase entity, float partialTick) {
		float f = scale;
		if (entity.isChild()) {
			f = (float)((double) f * 0.5D);
		}
		GL11.glScalef(f, f, f);
	}
}
