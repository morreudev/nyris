package com.morreu.nyris.data;

import org.bukkit.Location;

public class BlockData {
    private final Location location;
    private final org.bukkit.block.data.BlockData blockData;
    
    public BlockData(Location location, org.bukkit.block.data.BlockData blockData) {
        this.location = location;
        this.blockData = blockData;
    }
    
    public Location getLocation() { return location; }
    public org.bukkit.block.data.BlockData getBlockData() { return blockData; }
}
