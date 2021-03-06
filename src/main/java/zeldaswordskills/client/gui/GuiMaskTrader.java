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

package zeldaswordskills.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import zeldaswordskills.inventory.ContainerMaskTrader;
import zeldaswordskills.lib.ModInfo;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiMaskTrader extends GuiContainer
{
	private static final ResourceLocation texture = new ResourceLocation(ModInfo.ID + ":textures/gui/gui_mask_trader.png");
	
	private GuiButton borrow;
	
	public GuiMaskTrader() {
		super(new ContainerMaskTrader());
	}

	@Override
	public void initGui() {
		super.initGui();
		buttonList.clear();
		borrow = new GuiButton(0, guiLeft + 68, guiTop + 142, 40, 20, "Borrow");
		borrow.enabled = ((ContainerMaskTrader) inventorySlots).canBorrow();
		buttonList.add(borrow);
	}
	
	@Override
	public void updateScreen() {
		super.updateScreen();
		borrow.enabled = ((ContainerMaskTrader) inventorySlots).canBorrow();
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		((ContainerMaskTrader) inventorySlots).borrowMask();
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int par1, int par2) {
		String s = I18n.getString("gui.zss.mask_trader.name");
		fontRenderer.drawString(s, xSize / 2 - fontRenderer.getStringWidth(s) / 2, ySize - 56, 4210752);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		mc.getTextureManager().bindTexture(texture);
		drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
	}
}
