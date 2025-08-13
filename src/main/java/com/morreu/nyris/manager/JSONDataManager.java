package com.morreu.nyris.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.morreu.nyris.data.MineData;
import com.morreu.nyris.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JSONDataManager implements DataManager {
    
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final Gson gson;
    private final File minesFile;
    
    public JSONDataManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.minesFile = new File(plugin.getDataFolder(), 
            config.getString("database.fallback.json_file", "mines.json"));
    }
    
    @Override
    public CompletableFuture<Boolean> saveMine(MineData mine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<MineData> existingMines = loadMinesSync();
                existingMines.add(mine);
                
                saveMinesToFile(existingMines);
                return true;
                
            } catch (Exception e) {
                
            }
            return false;
        });
    }
    
    @Override
    public CompletableFuture<List<MineData>> loadMines() {
        return CompletableFuture.supplyAsync(() -> loadMinesSync());
    }
    
    private List<MineData> loadMinesSync() {
        try {
            if (!minesFile.exists()) {
                return new ArrayList<>();
            }
            
            try (FileReader reader = new FileReader(minesFile)) {
                List<MineDataSerializable> serializableMines = gson.fromJson(reader, 
                    new TypeToken<List<MineDataSerializable>>(){}.getType());
                
                if (serializableMines != null) {
                    List<MineData> mines = new ArrayList<>();
                    for (MineDataSerializable sm : serializableMines) {
                        try {
                            MineData mine = sm.toMineData();
                            if (mine != null && mine.getLocation().getWorld() != null) {
                                mines.add(mine);
                            }
                        } catch (Exception e) {
                            
                        }
                    }
                    return mines;
                }
            }
            
        } catch (IOException e) {
            
        }
        
        return new ArrayList<>();
    }
    
    @Override
    public CompletableFuture<Boolean> removeMine(MineData mine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<MineData> existingMines = loadMinesSync();
                existingMines.removeIf(m -> 
                    m.getOwnerUUID().equals(mine.getOwnerUUID()) &&
                    m.getLocation().getWorld().getName().equals(mine.getLocation().getWorld().getName()) &&
                    m.getLocation().getX() == mine.getLocation().getX() &&
                    m.getLocation().getY() == mine.getLocation().getY() &&
                    m.getLocation().getZ() == mine.getLocation().getZ()
                );
                
                saveMinesToFile(existingMines);
                return true;
                
            } catch (Exception e) {
                
            }
            return false;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> updateMine(MineData mine) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<MineData> existingMines = loadMinesSync();
                
                existingMines.removeIf(m -> 
                    m.getOwnerUUID().equals(mine.getOwnerUUID()) &&
                    m.getLocation().getWorld().getName().equals(mine.getLocation().getWorld().getName()) &&
                    m.getLocation().getX() == mine.getLocation().getX() &&
                    m.getLocation().getY() == mine.getLocation().getY() &&
                    m.getLocation().getZ() == mine.getLocation().getZ()
                );
                
                existingMines.add(mine);
                
                saveMinesToFile(existingMines);
                return true;
                
            } catch (Exception e) {
                
            }
            return false;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> isConnected() {
        return CompletableFuture.completedFuture(minesFile.exists() || 
            (minesFile.getParentFile() != null && minesFile.getParentFile().canWrite()));
    }
    
    @Override
    public void close() {
        
    }
    
    private void saveMinesToFile(List<MineData> mines) throws IOException {
        if (!minesFile.exists()) {
            minesFile.getParentFile().mkdirs();
            minesFile.createNewFile();
        }
        
        List<MineDataSerializable> serializableMines = new ArrayList<>();
        for (MineData mine : mines) {
            serializableMines.add(new MineDataSerializable(mine));
        }
        
        String json = gson.toJson(serializableMines);
        try (FileWriter writer = new FileWriter(minesFile)) {
            writer.write(json);
        }
    }
    
    private static class MineDataSerializable {
        private final UUID ownerUUID;
        private final String worldName;
        private final double x, y, z;
        private final int side;
        private final int height;
        private final List<BlockDataSerializable> originalBlocks;
        private final List<BlockSerializable> mineBlocks;
        private final long expirationTime;
        private final long pauseStartTime;
        private final long remainingTime;
        
        public MineDataSerializable(MineData mine) {
            this.ownerUUID = mine.getOwnerUUID();
            this.worldName = mine.getLocation().getWorld().getName();
            this.x = mine.getLocation().getX();
            this.y = mine.getLocation().getY();
            this.z = mine.getLocation().getZ();
            this.side = mine.getSide();
            this.height = mine.getHeight();
            this.originalBlocks = new ArrayList<>();
            this.mineBlocks = new ArrayList<>();
            this.expirationTime = mine.getExpirationTime();
            this.pauseStartTime = mine.getPauseStartTime();
            this.remainingTime = mine.getRemainingTime();
            
            for (BlockData blockData : mine.getOriginalBlocks()) {
                originalBlocks.add(new BlockDataSerializable(blockData));
            }
            
            for (org.bukkit.block.Block block : mine.getMineBlocks()) {
                mineBlocks.add(new BlockSerializable(block));
            }
        }
        
        public MineData toMineData() {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) return null;
            
            Location location = new Location(world, x, y, z);
            List<BlockData> originalBlocksList = new ArrayList<>();
            List<org.bukkit.block.Block> mineBlocksList = new ArrayList<>();
            
            for (BlockDataSerializable bds : originalBlocks) {
                BlockData blockData = bds.toBlockData();
                if (blockData != null) {
                    originalBlocksList.add(blockData);
                }
            }
            
            for (BlockSerializable bs : mineBlocks) {
                mineBlocksList.add(bs.toBlock());
            }
            
            return new MineData(ownerUUID, location, side, height, originalBlocksList, mineBlocksList, expirationTime, pauseStartTime, remainingTime);
        }
    }
    
    private static class BlockDataSerializable {
        private final String worldName;
        private final double x, y, z;
        private final String material;
        private final String blockData;
        
        public BlockDataSerializable(BlockData blockData) {
            this.worldName = blockData.getLocation().getWorld().getName();
            this.x = blockData.getLocation().getX();
            this.y = blockData.getLocation().getY();
            this.z = blockData.getLocation().getZ();
            this.material = blockData.getBlockData().getMaterial().name();
            this.blockData = blockData.getBlockData().getAsString();
        }
        
        public BlockData toBlockData() {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) return null;
            
            Location location = new Location(world, x, y, z);
            
            try {
                org.bukkit.block.data.BlockData bukkitBlockData = org.bukkit.Bukkit.createBlockData(material);
                return new BlockData(location, bukkitBlockData);
            } catch (IllegalArgumentException e) {
                try {
                    org.bukkit.block.data.BlockData fallbackData = org.bukkit.Bukkit.createBlockData("STONE");
                    return new BlockData(location, fallbackData);
                } catch (IllegalArgumentException fallbackError) {
                    return null;
                }
            }
        }
    }
    
    private static class BlockSerializable {
        private final String worldName;
        private final double x, y, z;
        
        public BlockSerializable(org.bukkit.block.Block block) {
            this.worldName = block.getWorld().getName();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
        }
        
        public org.bukkit.block.Block toBlock() {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) return null;
            
            return world.getBlockAt((int)x, (int)y, (int)z);
        }
    }
}
