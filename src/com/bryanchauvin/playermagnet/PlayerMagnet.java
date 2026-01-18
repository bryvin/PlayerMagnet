package com.bryanchauvin.playermagnet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerMagnet extends JavaPlugin implements Listener {
	
	private double maxDistance = 8;
	
	private final List<UUID> magnetPlayers = new CopyOnWriteArrayList<UUID>();
	
	private static class MagnetTarget {
		private final Item item;
		private final Player player;
		private final double distanceSquared;
		
		private MagnetTarget(Item item, Player player, double distanceSquared) {
			this.item = item;
			this.player = player;
			this.distanceSquared = distanceSquared;
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(cmd.getName().equalsIgnoreCase("magnet")) {
			if(!(sender instanceof Player)) {
				sender.sendMessage("Only players can use this command.");
			} else {
				Player player = (Player) sender;
				UUID playerId = player.getUniqueId();
				
				// No args toggles magnet on/off
				if(args.length == 0) {
					if(magnetPlayers.contains(playerId)) {
						magnetPlayers.remove(playerId);
						player.sendMessage("You turned off your magnet.");
					} else {
						magnetPlayers.add(playerId);
						player.sendMessage("You turned on your magnet.");
					}
				} else if(args.length == 1) {
					// Keep on/off for compatibility
					String arg = args[0].toLowerCase();
					
					if(arg.equals("on")) {
						if(!magnetPlayers.contains(playerId)) {
							magnetPlayers.add(playerId);
							player.sendMessage("You turned on your magnet.");
						} else {
							player.sendMessage("Your magnet is already on!");
						}
					} else if(arg.equals("off")) {
						if(magnetPlayers.contains(playerId)) {
							magnetPlayers.remove(playerId);
							player.sendMessage("You turned off your magnet.");
						} else {
							player.sendMessage("Your magnet is already off!");
						}
					} else {
						sender.sendMessage("Usage: /magnet [on|off]");
					}
				} else {
					sender.sendMessage("Usage: /magnet [on|off]");
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
		maxDistance = getConfig().getDouble("max-distance", 8.0);
		
		// Register events
		getServer().getPluginManager().registerEvents(this, this);
		
		ItemSearch itemSearch = new ItemSearch();
		getServer().getScheduler().runTaskTimer(this, itemSearch, 5L, 5L);
	}
	
	@Override
	public void onDisable() {
		// Clear state on shutdown/reload
		magnetPlayers.clear();
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		// Remove player so we never target a stale player instance/last location
		magnetPlayers.remove(event.getPlayer().getUniqueId());
	}
	
	@EventHandler
	public void onPlayerKick(PlayerKickEvent event) {
		// Remove player so we never target a stale player instance/last location
		magnetPlayers.remove(event.getPlayer().getUniqueId());
	}
	
	private class ItemSearch implements Runnable {

		@Override
		public void run() {
			// Nothing to do if nobody has magnet on
			if(magnetPlayers.isEmpty()) {
				return;
			}
			
			// Clean up invalid/offline players
			magnetPlayers.removeIf(playerId -> playerId == null || getServer().getPlayer(playerId) == null);
			if(magnetPlayers.isEmpty()) {
				return;
			}
			
			double maxDistanceSquared = maxDistance * maxDistance;
			Map<UUID, MagnetTarget> closestTargets = new HashMap<UUID, MagnetTarget>();
			
			// Search near magnet-enabled players instead of scanning every world/entity
			for(UUID playerId : magnetPlayers) {
				Player player = getServer().getPlayer(playerId);
				if(player == null || !player.isOnline()) {
					continue;
				}
				
				Location playerLocation = player.getLocation();
				
				for(Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
					// We only care about item entities
					if(!(entity instanceof Item)) {
						continue;
					}
					
					Item item = (Item) entity;
					ItemStack stack = item.getItemStack();
					Location itemLocation = item.getLocation();
					
					// Make sure item is valid to be picked up at all
					if(stack.getAmount() <= 0 || item.isDead() || (item.getPickupDelay() > item.getTicksLived())) {
						continue;
					}
					
					// getNearbyEntities() is a cube search; clamp to a sphere distance
					double distanceSquared = playerLocation.distanceSquared(itemLocation);
					if(distanceSquared > maxDistanceSquared) {
						continue;
					}
					
					UUID itemId = item.getUniqueId();
					MagnetTarget existing = closestTargets.get(itemId);
					
					// This player is closer to the item
					if(existing == null || distanceSquared < existing.distanceSquared) {
						closestTargets.put(itemId, new MagnetTarget(item, player, distanceSquared));
					}
				}
			}
			
			// Set velocity for each item towards the closest player found
			for(MagnetTarget target : closestTargets.values()) {
				if(target.item == null || target.player == null) {
					continue;
				}
				
				if(target.item.isDead() || !target.player.isOnline()) {
					continue;
				}
				
				target.item.setVelocity(target.player.getLocation().toVector().subtract(target.item.getLocation().toVector()).normalize());
			}
		}
	}
}