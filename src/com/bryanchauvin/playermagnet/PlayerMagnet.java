package com.bryanchauvin.playermagnet;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerMagnet extends JavaPlugin {
	
	private double maxDistance = 8;
	
	private final List<Player> magnetPlayers = new CopyOnWriteArrayList<Player>();
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(cmd.getName().equalsIgnoreCase("magnet")) {
			if(!(sender instanceof Player)) {
				sender.sendMessage("Only players can use this command.");
			} else {
				Player player = (Player) sender;
				
				if(args.length == 1) {
					String arg = args[0].toLowerCase();
					
					if(arg.equals("on")) {
						if(!magnetPlayers.contains(player)) {
							magnetPlayers.add(player);
							player.sendMessage("You turned on your magnet.");
						} else {
							player.sendMessage("Your magnet is already on!");
						}
					} else if(arg.equals("off")) {
						if(magnetPlayers.contains(player)) {
							magnetPlayers.remove(player);
							player.sendMessage("You turned off your magnet.");
						} else {
							player.sendMessage("Your magnet is already off!");
						}
					}
				} else {
					sender.sendMessage("Usage: /magnet <on|off>");
				}
			}
			
			return true;
		}
		
		return false;
	}

	@Override
	public void onEnable() {
		// Save default config if not already
		saveDefaultConfig();
		
		// Load config
		maxDistance = getConfig().getInt("max-distance");
		
		ItemSearch itemSearch = new ItemSearch();
		getServer().getScheduler().scheduleSyncRepeatingTask(this, itemSearch, 5, 5);
	}
	
	private class ItemSearch implements Runnable {

		@Override
		public void run() {
			for(World world : getServer().getWorlds()) {
				for(Entity entity : world.getEntities()) {
					// We only care about item entities
					if(!(entity instanceof Item)) {
						continue;
					}
					
					Item item = (Item) entity;
					ItemStack stack = item.getItemStack();
					Location location = item.getLocation();
					
					// Make sure item is valid to be picked up at all
					if(stack.getAmount() <= 0 || item.isDead() || (item.getPickupDelay() > item.getTicksLived())) {
						continue;
					}
					
					Player closestPlayer = null;
					double distanceSmall = maxDistance;
					
					// Check each player with magnet ON for distance
					for(Player player : magnetPlayers) {
						if(player != null) {
							double playerDistance = player.getLocation().distance(location);
							
							// This player is closer
							if(playerDistance < distanceSmall) {
								distanceSmall = playerDistance;
								closestPlayer = player;
							}
						}
					}
					
					if(closestPlayer == null) {
						continue;
					}
					
					// Set velocity for items towards player
					item.setVelocity(closestPlayer.getLocation().toVector().subtract(item.getLocation().toVector()).normalize());
				}
			}
		}
	}
}