package com.github.namrufus.Porthos;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;

public class Porthos extends JavaPlugin implements Listener {
	private boolean fixOverworldPortalExit;
	private boolean forceNetherSamePortal;

	private double netherRatio;

	private String messageIn;
	
	private ItemRestriction inItems;
	private ItemRestriction outItems;
	
	private class ItemRestriction {
		public boolean defaultDrop;
		public HashSet<Material> blacklist;
		public HashSet<Material> whitelist;
	}
	
	private HashMap<String, PlayerRecord> portalCoords;
	
	private class PlayerRecord {
		public Location overworldPortalLocation;
		public Location netherPortalLocation;
		public boolean gotMessage;
	}
	
	public void onEnable() {
		// perform check for config file, if it doesn't exist, then create it using the default config file
		if (!this.getConfig().isSet("porthos")) {
			this.saveDefaultConfig();
			this.getLogger().warning("Config did not exist or was invalid, default config saved.");
		}
		this.reloadConfig();
		
		// load configurations
		ConfigurationSection config = getConfig().getConfigurationSection("porthos");
		
		fixOverworldPortalExit = config.getBoolean("fix_overworld_portal_exit");
		forceNetherSamePortal = config.getBoolean("force_nether_same_portal");
		
		netherRatio = config.getDouble("nether_ratio");
		
		messageIn = config.getString("message_in");
		
		inItems = new ItemRestriction();
		outItems = new ItemRestriction();
		
		inItems.blacklist = getMaterialSet("porthos.blacklist_in");
		outItems.blacklist = getMaterialSet("porthos.blacklist_out");
		
		inItems.whitelist = getMaterialSet("porthos.whitelist_in");
		outItems.whitelist = getMaterialSet("porthos.whitelist_out");
		
		inItems.defaultDrop = config.getBoolean("default_drop_in");
		outItems.defaultDrop = config.getBoolean("default_drop_out");
		
		// load player data
		portalCoords = new HashMap<String, PlayerRecord>();
		
        File file = new File(getDataFolder(), "PortalCoords.yml");
	    FileConfiguration playerDataConfig = YamlConfiguration.loadConfiguration(file);
	    
	    for (String playerName: playerDataConfig.getKeys(false)) {
	    	PlayerRecord record = new PlayerRecord();
	    	record.gotMessage = false;
	    	
	    	record.netherPortalLocation = new Location(getServer().getWorlds().get(1), 0, 0, 0);
	    	record.overworldPortalLocation = new Location(getServer().getWorlds().get(0), 0, 0, 0);
	    	
	    	record.overworldPortalLocation.setX(playerDataConfig.getDouble(playerName+".overworld.x"));
	    	record.overworldPortalLocation.setY(playerDataConfig.getDouble(playerName+".overworld.y"));
	    	record.overworldPortalLocation.setZ(playerDataConfig.getDouble(playerName+".overworld.z"));
	    	
	    	record.netherPortalLocation.setX(playerDataConfig.getDouble(playerName+".nether.x"));
	    	record.netherPortalLocation.setY(playerDataConfig.getDouble(playerName+".nether.y"));
	    	record.netherPortalLocation.setZ(playerDataConfig.getDouble(playerName+".nether.z"));
	    	
	    	portalCoords.put(playerName, record);
	    }
		
	    // register portal events
		getServer().getPluginManager().registerEvents(this, this);
	}
	private Material getMaterial(String materialName) {
		Material material = Material.getMaterial(materialName);
		if (material == null)
			getLogger().warning("Unknown Material in Config: "+materialName);
		return material;
	}
	private HashSet<Material> getMaterialSet(String path) {
		HashSet<Material> materials = new HashSet<Material>();
		
		List<String> materialList = getConfig().getStringList(path);
		
		for (String materialName: materialList) {
			Material material = getMaterial(materialName);
			if (material != null)
				materials.add(material);
		}
		
		return materials;
	}
	
	public void onDisable() {
        File file = new File(getDataFolder(), "PortalCoords.yml");
	    FileConfiguration playerDataConfig = YamlConfiguration.loadConfiguration(file);
	    
	    for (String playerName: portalCoords.keySet()) {
	    	PlayerRecord record = portalCoords.get(playerName);
	    	
	    	playerDataConfig.set(playerName+".overworld.x", record.overworldPortalLocation.getX());
	    	playerDataConfig.set(playerName+".overworld.y", record.overworldPortalLocation.getY());
	    	playerDataConfig.set(playerName+".overworld.z", record.overworldPortalLocation.getZ());
	    	
	    	playerDataConfig.set(playerName+".nether.x", record.netherPortalLocation.getX());
	    	playerDataConfig.set(playerName+".nether.y", record.netherPortalLocation.getY());
	    	playerDataConfig.set(playerName+".nether.z", record.netherPortalLocation.getZ());
	    }
	    
	    try {
	    playerDataConfig.save(file);
	    } catch (IOException e) {
	    	getLogger().warning("Can't save player data!");
	    	e.printStackTrace();
	    }
	}
	
	@EventHandler
	public void onPlayerPortalEvent(PlayerPortalEvent event) {
		Player player = event.getPlayer();
		
		if (player.getGameMode() == GameMode.CREATIVE)
			return;
		
		if (event.getCause() != TeleportCause.NETHER_PORTAL)
			return;
			
		if (event.getTo().getWorld().getEnvironment() == Environment.NETHER) {
			restrictItems(player, inItems);
			
			// when teleporting to the nether, record the location
			PlayerRecord record = new PlayerRecord();
			record.gotMessage = true;
			record.overworldPortalLocation = event.getPlayer().getLocation();
			
			// manually determine the portal location and teleport the player
			// so that the recorded coordinates are correct
			Location location = player.getLocation().multiply(netherRatio);
			location.setWorld(getServer().getWorlds().get(1));
			
			location = event.getPortalTravelAgent().findOrCreate(location);
			location.add(new Vector(0.5, 0.0, 0.5));
			
			event.getPlayer().teleport(location);
			event.setCancelled(true);
			record.netherPortalLocation = location;
			
			portalCoords.put(event.getPlayer().getName(), record);
		}
		else {
			// when teleporting out of the nether, force the player to exit from the
			// same portal link
			PlayerRecord record = portalCoords.get(event.getPlayer().getName());
			if (record == null)
				return;
			
			if (forceNetherSamePortal && event.getPlayer().getLocation().distanceSquared(record.netherPortalLocation) > 9/*3meters*/) {
				event.getPlayer().sendMessage("§7[Porthos] You must exit the nether from the same portal you entered it from!");
				Vector v = record.netherPortalLocation.toVector();
				String coordStr = "("+v.getBlockX()+", "+v.getBlockY()+", "+v.getBlockZ()+")";
				event.getPlayer().sendMessage("§7[Porthos] Portal Location: "+coordStr);
				event.setCancelled(true);
				return;
			}
			
			restrictItems(player, outItems);
			
			if (fixOverworldPortalExit) {
				event.getPlayer().teleport(record.overworldPortalLocation);
				event.setCancelled(true);
			}
		}
	}
	@EventHandler
	public void onEntityPortalEvent(EntityPortalEvent event) {
		event.setCancelled(true);
	}
	@EventHandler
	public void onEntityPortalEnterEvent (EntityPortalEnterEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof Player) {
			Player player = (Player) entity;
			if (!portalCoords.containsKey(player.getName())) {
				PlayerRecord record = new PlayerRecord();
				record.gotMessage = true;
				record.overworldPortalLocation = player.getLocation();
				record.netherPortalLocation = player.getLocation();
				portalCoords.put(player.getName(), record);
			}

			if (!portalCoords.get(player.getName()).gotMessage)
				player.sendMessage("§c[Porthos] "+messageIn);
			portalCoords.get(player.getName()).gotMessage = true;
		}
	}
	
	private void restrictItems(Player player, ItemRestriction itemRestriction) {
		for (ItemStack itemStack : player.getInventory().getContents()) {
			if (itemStack != null) {
				// drop the item if items are to be dropped by default and the item type is not whitelisted
				// also drop the item if it is blacklisted
				boolean whitelisted = itemRestriction.whitelist.contains(itemStack.getType());
				boolean blacklisted = itemRestriction.blacklist.contains(itemStack.getType());
				if ((itemRestriction.defaultDrop && !whitelisted) || blacklisted) {
			        player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
			        player.getInventory().remove(itemStack);
			    }
			}
		}
	}
}
