package xyz.derkades.serverselectorx;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import xyz.derkades.derkutils.Cooldown;

public class SelectorOpenListener implements Listener {

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(final PlayerInteractEvent event){
		if (event.getAction() == Action.PHYSICAL) {
			return;
		}

		if (!event.getHand().equals(EquipmentSlot.HAND)) {
			return;
		}


		final Player player = event.getPlayer();

		if (Cooldown.getCooldown("ssx-global-open" + player.getName()) > 0) {
			return;
		}

		Cooldown.addCooldown("ssx-global-open" + player.getName(), 300);

		for (final FileConfiguration config : Main.getConfigurationManager().allFiles()) {
			if (config == null) {
				continue;
			}

			if (!Main.isSelectorItem(player.getInventory().getItemInMainHand(), config)) {
				continue;
			}

			if (!SelectorMenu.checkPermission(player, config)) {
				return;
			}

			new SelectorMenu(player, config);
			return;
		}
	}

}
