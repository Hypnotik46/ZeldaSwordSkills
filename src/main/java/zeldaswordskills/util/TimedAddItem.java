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

import java.util.Timer;
import java.util.TimerTask;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class TimedAddItem {
	Timer timer;
	final EntityPlayer player;
	final ItemStack stack;
	/**
	 * Adds the ItemStack to the player's inventory after the given delay (in milliseconds)
	 */
	public TimedAddItem(EntityPlayer player, ItemStack stack, int delay) {
		this.player = player;
		this.stack = stack;
		timer = new Timer();
		timer.schedule(new AddItemTask(), delay);
	}

	class AddItemTask extends TimerTask {
		@Override
		public void run() {
			PlayerUtils.addItemToInventory(player, stack);
			PlayerUtils.playSound(player, "random.pop", 1.0F,
					((player.worldObj.rand.nextFloat() -
					player.worldObj.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
			timer.cancel();
		}
	}
}
