package io.github.townyadvanced.townyprovinces.jobs.land_validation;

import io.github.townyadvanced.townyprovinces.TownyProvinces;
import io.github.townyadvanced.townyprovinces.data.TownyProvincesDataHolder;
import io.github.townyadvanced.townyprovinces.objects.Province;
import io.github.townyadvanced.townyprovinces.objects.TPCoord;
import io.github.townyadvanced.townyprovinces.settings.TownyProvincesSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class LandvalidationTask extends BukkitRunnable {
	
	@Override
	public void run() {
		//Execute the land validation job
		synchronized (TownyProvinces.MAP_CHANGE_LOCK) {
			TownyProvinces.info("Land Validation Job Starting.");
			executeLandValidation();
		}
	}
	
	/**
	 * Go through each province,
	 * And decide if it is land or sea,
	 * then set the isSea boolean as appropriate
	 * <p>
	 * This method will not always work perfectly
	 * because it checks only a selection if the biomes.
	 * It does this because checking a biome is hard on the processor
	 * <p>
	 * Mistakes are expected,
	 * which is why server owners can run /tp province sea x,y
	 */
	private void executeLandValidation() {
		TownyProvinces.info("Now Running land validation job.");
		double numProvincesProcessed = 0;
		for(Province province : TownyProvincesDataHolder.getInstance().getProvincesSet()) {
			if(!province.isLandValidationRequested())
				numProvincesProcessed++;  //Already processed
		}
		for(Province province: TownyProvincesDataHolder.getInstance().getProvincesSet()) {
			if (province.isLandValidationRequested()) {
				boolean isSea = isProvinceMainlyOcean(province);
				if(isSea != province.isSea()) {
					province.setSea(isSea);
				}
				province.setLandValidationRequested(false);
				province.saveData();
				numProvincesProcessed++;
			}
			int percentCompletion = (int)((numProvincesProcessed / TownyProvincesDataHolder.getInstance().getProvincesSet().size()) * 100);
			TownyProvinces.info("Land Validation Job Progress: " + percentCompletion + "%");

			//Handle any stop requests
			LandValidationJobStatus landValidationJobStatus = LandValidationTaskController.getLandValidationJobStatus();
			switch (landValidationJobStatus) {
				case STOP_REQUESTED:
					LandValidationTaskController.stopTask();
					return;
				case PAUSE_REQUESTED:
					LandValidationTaskController.pauseTask();
					return;
				case RESTART_REQUESTED:
					LandValidationTaskController.restartTask();
					return;
			}
		}
		LandValidationTaskController.stopTask();
		TownyProvinces.info("Land Validation Job Complete.");
	}

	private static boolean isProvinceMainlyOcean(Province province) {
		List<TPCoord> coordsInProvince = province.getCoordsInProvince();
		String worldName = TownyProvincesSettings.getWorldName();
		World world = Bukkit.getWorld(worldName);
		Biome biome;
		TPCoord coordToTest;
		for(int i = 0; i < 10; i++) {
			coordToTest = coordsInProvince.get((int)(Math.random() * coordsInProvince.size()));
			int x = (coordToTest.getX() * TownyProvincesSettings.getChunkSideLength()) + 8;
			int z = (coordToTest.getZ() * TownyProvincesSettings.getChunkSideLength()) + 8;
			biome = world.getHighestBlockAt(x,z).getBiome();
			System.gc();
			try {
				//Sleep as the above check is hard on processor
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			if(!biome.name().toLowerCase().contains("ocean") && !biome.name().toLowerCase().contains("beach")) {
				return false;
			}
		}
		return true;
	}

}