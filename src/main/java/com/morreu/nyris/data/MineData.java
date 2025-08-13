package com.morreu.nyris.data;

import org.bukkit.Location;
import org.bukkit.block.Block;
import java.util.List;
import java.util.UUID;

public class MineData {
    private final UUID ownerUUID;
    private final Location location;
    private final int side;
    private final int height;
    private final List<BlockData> originalBlocks;
    private final List<Block> mineBlocks;
    private final long expirationTime;
    private long pauseStartTime;
    private long remainingTime;
    
    public MineData(UUID ownerUUID, Location location, int side, int height, 
                    List<BlockData> originalBlocks, List<Block> mineBlocks, long expirationTime) {
        this.ownerUUID = ownerUUID;
        this.location = location;
        this.side = side;
        this.height = height;
        this.originalBlocks = originalBlocks;
        this.mineBlocks = mineBlocks;
        this.expirationTime = expirationTime;
        this.pauseStartTime = System.currentTimeMillis();
        this.remainingTime = expirationTime - System.currentTimeMillis();
    }
    
    public MineData(UUID ownerUUID, Location location, int side, int height, 
                    List<BlockData> originalBlocks, List<Block> mineBlocks, long expirationTime, long pauseStartTime) {
        this.ownerUUID = ownerUUID;
        this.location = location;
        this.side = side;
        this.height = height;
        this.originalBlocks = originalBlocks;
        this.mineBlocks = mineBlocks;
        this.expirationTime = expirationTime;
        this.pauseStartTime = pauseStartTime;
        this.remainingTime = expirationTime - pauseStartTime;
    }
    
    public MineData(UUID ownerUUID, Location location, int side, int height, 
                    List<BlockData> originalBlocks, List<Block> mineBlocks, long expirationTime, long pauseStartTime, long remainingTime) {
        this.ownerUUID = ownerUUID;
        this.location = location;
        this.side = side;
        this.height = height;
        this.originalBlocks = originalBlocks;
        this.mineBlocks = mineBlocks;
        this.expirationTime = expirationTime;
        this.pauseStartTime = pauseStartTime;
        this.remainingTime = remainingTime;
    }
    
    public UUID getOwnerUUID() { return ownerUUID; }
    public Location getLocation() { return location; }
    public int getSide() { return side; }
    public int getHeight() { return height; }
    public List<BlockData> getOriginalBlocks() { return originalBlocks; }
    public List<Block> getMineBlocks() { return mineBlocks; }
    public long getExpirationTime() { return expirationTime; }
    public long getPauseStartTime() { return pauseStartTime; }
    public long getRemainingTime() { return remainingTime; }
    
    public void setPauseStartTime(long pauseStartTime) {
        this.pauseStartTime = pauseStartTime;
    }
    
    public void setRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;
    }
    
    public long getAdjustedExpirationTime() {
        long currentTime = System.currentTimeMillis();
        return currentTime + remainingTime;
    }
    
    public boolean isEmpty() {
        return mineBlocks.isEmpty();
    }
    
    public void removeBlock(Block block) {
        mineBlocks.remove(block);
    }
}
