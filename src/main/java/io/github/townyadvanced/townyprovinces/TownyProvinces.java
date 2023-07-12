package io.github.townyadvanced.townyprovinces;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.initialization.TownyInitException;
import com.palmergames.bukkit.towny.object.Translatable;
import com.palmergames.bukkit.towny.object.TranslationLoader;
import com.palmergames.bukkit.util.Version;
import de.bluecolored.bluemap.api.BlueMapAPI;
import io.github.townyadvanced.townyprovinces.commands.TownyProvincesAdminCommand;
import io.github.townyadvanced.townyprovinces.data.DataHandlerUtil;
import io.github.townyadvanced.townyprovinces.data.TownyProvincesDataHolder;
import io.github.townyadvanced.townyprovinces.jobs.map_display.DisplayProvincesOnBlueMapAction;
import io.github.townyadvanced.townyprovinces.jobs.map_display.DisplayProvincesOnDynmapAction;
import io.github.townyadvanced.townyprovinces.jobs.map_display.DisplayProvincesOnPl3xMapV3Action;
import io.github.townyadvanced.townyprovinces.jobs.map_display.MapDisplayTaskController;
import io.github.townyadvanced.townyprovinces.listeners.TownyListener;
import io.github.townyadvanced.townyprovinces.messaging.Messaging;
import io.github.townyadvanced.townyprovinces.settings.Settings;
import io.github.townyadvanced.townyprovinces.settings.TownyProvincesSettings;
import io.github.townyadvanced.townyprovinces.util.FileUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import static com.palmergames.util.JavaUtil.classExists;

public class TownyProvinces extends JavaPlugin {
	/**
	 * Lock this if you want to change or display the map,
	 * to avoid concurrent modification problems
	 */
	public static final Object MAP_DISPLAY_LOCK = new Object();
	public static final Object LAND_VALIDATION_LOCK = new Object();
	private static TownyProvinces plugin;
	private static final Version requiredTownyVersion = Version.fromString("0.99.1.0");
	
	@Override
	public void onEnable() {
		plugin = this;

		//Load Mandatory stuff
		if(!checkTownyVersion()
				|| !loadConfig()
				|| !loadLocalization(false)
				|| !validateWorld()
				|| !TownyProvincesSettings.isTownyProvincesEnabled() 
				|| !TownyProvincesDataHolder.initialize()
				|| !FileUtil.setupPluginDataFoldersIfRequired()
				|| !FileUtil.createRegionDefinitionsFolderAndSampleFiles()
				|| !DataHandlerUtil.loadAllData()
				|| !registerListeners()
				|| !registerAdminCommands()
			) {
			severe("TownyProvinces Did Not Load Successfully.");
			onDisable();
			return;
		}

		//Load optional stuff 
		loadIntegrations();

		info("TownyProvinces Loaded Successfully");
	}

	public void reloadConfigsAndData() {
		if(!loadConfig()
			|| !loadLocalization(false)
			|| !TownyProvincesDataHolder.initialize()
			|| !FileUtil.setupPluginDataFoldersIfRequired()
			|| !FileUtil.createRegionDefinitionsFolderAndSampleFiles()
			|| !DataHandlerUtil.loadAllData()
		) {
			severe("TownyProvinces Did Not Reload Successfully.");
			onDisable();
			return;
		}

		//Refresh 
		MapDisplayTaskController.requestFullMapRefresh();
		info("TownyProvinces Reloaded Successfully");
	}

	private boolean registerAdminCommands() {
		getCommand("townyprovincesadmin").setExecutor(new TownyProvincesAdminCommand());
		return true;
	}
	
	private boolean loadIntegrations() {
		try {
			if (getServer().getPluginManager().isPluginEnabled("Pl3xMap")) {
				if (classExists("net.pl3x.map.core.Pl3xMap")) {
					info("Found Pl3xMap v3. Enabling Pl3xMap integration.");
					MapDisplayTaskController.addMapDisplayAction(new DisplayProvincesOnPl3xMapV3Action());
				}
				else if (classExists("net.pl3x.map.Pl3xMap")) {
					//Pl3xMap v2
					info("Pl3xMap v2 is not supported. Cannot enable Pl3xMap integration.");
				}
				else {
					//Pl3xMap v1
					info("Pl3xMap v1 is not supported. Cannot enable Pl3xMap integration.");
				}
			}
			if(getServer().getPluginManager().isPluginEnabled("bluemap")){
				info("Found BlueMap. Enabling BlueMap integration.");
				BlueMapAPI.onEnable(e -> {
					/** This basically turns the BufferedImage into a readable format(png) for bluemap markers to read
					 *  I put it here, so it won't just fire everytime the scheduler fires and only when it restarts
					 *  */
					Path assetsFolder = e.getWebApp().getWebRoot().resolve("assets");
					try(OutputStream out = Files.newOutputStream(assetsFolder.resolve("province.png"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)){
						ImageIO.write(TownyProvincesSettings.getTownCostsIcon(),"png", out);
					}catch (IOException ex) {
						TownyProvinces.severe("Failed to put BlueMap Marker Icon as png file!");
						throw new RuntimeException(ex);
					}
				});
				BlueMapAPI.onEnable(blueMapAPI -> MapDisplayTaskController.addMapDisplayAction(new DisplayProvincesOnBlueMapAction()));
			}
			if (getServer().getPluginManager().isPluginEnabled("dynmap")) {
				info("Found Dynmap plugin. Enabling Dynmap integration.");
				MapDisplayTaskController.addMapDisplayAction(new DisplayProvincesOnDynmapAction());
			}
			if (!MapDisplayTaskController.isMapSupported()) {
				info("Did not find a supported map plugin. Cannot enable map integration.");
				return false;
			}
			MapDisplayTaskController.startTask();
			return true;
		} catch (Exception e) {
			Messaging.sendErrorMsg(Bukkit.getConsoleSender(), "Problem enabling map integration: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean checkTownyVersion() {
		if (!townyVersionCheck()) {
			severe("Towny version does not meet required minimum version: " + requiredTownyVersion);
			return false;
		} else {
			info("Towny version " + getTownyVersion() + " found.");
			return true;
		}
	}
	
	private boolean loadConfig() {
		try {
			Settings.loadConfig();
		} catch (TownyInitException e) {
			e.printStackTrace();
			severe("Config.yml failed to load! Disabling!");
			return false;
		}
		info("Config.yml loaded successfully.");
		return true;
	}

	public static boolean loadLocalization(boolean reload) {
		try {
			Plugin plugin = getPlugin(); 
			Path langFolderPath = Paths.get(plugin.getDataFolder().getPath()).resolve("lang");
			TranslationLoader loader = new TranslationLoader(langFolderPath, plugin, TownyProvinces.class);
			loader.load();
			TownyAPI.getInstance().addTranslations(plugin, loader.getTranslations());
		} catch (TownyInitException e) {
			e.printStackTrace();
			severe("Locale files failed to load! Disabling!");
			return false;
		}
		if (reload) {
			info(Translatable.of("msg_reloaded_lang").defaultLocale());
		}
		return true;
	}

	private boolean validateWorld() {
		info("Now validating world");
		World world = TownyProvincesSettings.getWorld();
		if(world != null) {
			info("World Validated");
			return true;
		} else {
			Messaging.sendErrorMsg(Bukkit.getConsoleSender(), Translatable.of("msg_err_unknown_world"));
			return false;
		}
	}
	
	private boolean registerListeners() {
		PluginManager pluginManager = this.getServer().getPluginManager();
		pluginManager.registerEvents(new TownyListener(), this);
		return true;
	}
	public String getVersion() {
		return this.getDescription().getVersion();
	}

	public static TownyProvinces getPlugin() {
		return plugin;
	}
	
	/**
	 * Use this in most cases, as it looks better
	 * @return plugin prefix
	 */
	public static String getTranslatedPrefix() {
		return Translatable.of("townyprovinces_plugin_prefix").translate(Locale.ROOT);
	}
	
	private boolean townyVersionCheck() {
		return Version.fromString(getTownyVersion()).compareTo(requiredTownyVersion) >= 0;
	}

	private String getTownyVersion() {
		return Bukkit.getPluginManager().getPlugin("Towny").getDescription().getVersion();
	}

	public static void info(String message) {
		plugin.getLogger().info(message);
	}

	public static void severe(String message) {
		plugin.getLogger().severe(message);
	}
}
