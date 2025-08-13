package com.morreu.nyris.mysql;

import com.morreu.nyris.data.BlockData;
import com.morreu.nyris.manager.DataManager;
import com.morreu.nyris.data.MineData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.Bukkit;
import java.util.Arrays;

public class MySQLDataManager implements DataManager {
    
    private final JavaPlugin plugin;
    private final HikariDataSource dataSource;
    private final FileConfiguration config;
    
    public MySQLDataManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.dataSource = createDataSource();
        createTables();
    }
    
    private HikariDataSource createDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        
        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "nyris_plugin");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "");
        
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + 
                               "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(config.getInt("database.mysql.connection_pool_size", 10));
        hikariConfig.setConnectionTimeout(config.getLong("database.mysql.connection_timeout", 30000));
        hikariConfig.setIdleTimeout(config.getLong("database.mysql.idle_timeout", 600000));
        hikariConfig.setMaxLifetime(config.getLong("database.mysql.max_lifetime", 1800000));
        
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(hikariConfig);
    }
    
    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String createMinesTable = """
                CREATE TABLE IF NOT EXISTS nyris_mines (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    world_name VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    side INT NOT NULL,
                    height INT NOT NULL,
                    expiration_time BIGINT NOT NULL,
                    pause_start_time BIGINT NOT NULL,
                    remaining_time BIGINT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_owner (owner_uuid),
                    INDEX idx_location (world_name, x, y, z),
                    INDEX idx_expiration (expiration_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            String createBlocksTable = """
                CREATE TABLE IF NOT EXISTS nyris_blocks (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    mine_id INT NOT NULL,
                    world_name VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    block_data TEXT,
                    is_original BOOLEAN NOT NULL,
                    FOREIGN KEY (mine_id) REFERENCES nyris_mines(id) ON DELETE CASCADE,
                    INDEX idx_mine (mine_id),
                    INDEX idx_location (world_name, x, y, z)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
            
            stmt.execute(createMinesTable);
            stmt.execute(createBlocksTable);
            
            migrateTableIfNeeded(conn);
            
        } catch (SQLException e) {
            
        }
    }
    
    private void migrateTableIfNeeded(Connection conn) {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'nyris_mines' AND column_name = 'remaining_time'";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, conn.getCatalog());
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getInt(1) == 0) {
                    String alterSql = "ALTER TABLE nyris_mines ADD COLUMN remaining_time BIGINT DEFAULT 0";
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterSql);
                    }
                    
                    String updateSql = "UPDATE nyris_mines SET remaining_time = GREATEST(0, expiration_time - ?)";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setLong(1, System.currentTimeMillis());
                        int updated = updateStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            
        }
    }
    
    @Override
    public CompletableFuture<Boolean> saveMine(MineData mine) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO nyris_mines (owner_uuid, world_name, x, y, z, side, height, expiration_time, pause_start_time, remaining_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, mine.getOwnerUUID().toString());
                stmt.setString(2, mine.getLocation().getWorld().getName());
                stmt.setDouble(3, mine.getLocation().getX());
                stmt.setDouble(4, mine.getLocation().getY());
                stmt.setDouble(5, mine.getLocation().getZ());
                stmt.setInt(6, mine.getSide());
                stmt.setInt(7, mine.getHeight());
                stmt.setLong(8, mine.getExpirationTime());
                stmt.setLong(9, mine.getPauseStartTime());
                stmt.setLong(10, mine.getRemainingTime());
                
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    return CompletableFuture.completedFuture(false);
                }
                
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long mineId = generatedKeys.getLong(1);
                        
                        for (BlockData blockData : mine.getOriginalBlocks()) {
                            String blockSql = "INSERT INTO nyris_blocks (mine_id, world_name, x, y, z, material, block_data) VALUES (?, ?, ?, ?, ?, ?, ?)";
                            try (PreparedStatement blockStmt = conn.prepareStatement(blockSql)) {
                                blockStmt.setLong(1, mineId);
                                blockStmt.setString(2, blockData.getLocation().getWorld().getName());
                                blockStmt.setDouble(3, blockData.getLocation().getX());
                                blockStmt.setDouble(4, blockData.getLocation().getY());
                                blockStmt.setDouble(5, blockData.getLocation().getZ());
                                blockStmt.setString(6, blockData.getBlockData().getMaterial().name());
                                blockStmt.setString(7, blockData.getBlockData().getAsString());
                                blockStmt.executeUpdate();
                            }
                        }
                        
                        for (Block block : mine.getMineBlocks()) {
                            if (block.getType() != Material.AIR) {
                                String blockSql = "INSERT INTO nyris_blocks (mine_id, world_name, x, y, z, material, block_data) VALUES (?, ?, ?, ?, ?, ?, ?)";
                                try (PreparedStatement blockStmt = conn.prepareStatement(blockSql)) {
                                    blockStmt.setLong(1, mineId);
                                    blockStmt.setString(2, block.getWorld().getName());
                                    blockStmt.setDouble(3, block.getX());
                                    blockStmt.setDouble(4, block.getY());
                                    blockStmt.setDouble(5, block.getZ());
                                    blockStmt.setString(6, block.getType().name());
                                    blockStmt.setString(7, block.getBlockData().getAsString());
                                    blockStmt.executeUpdate();
                                }
                            }
                        }
                    }
                }
                
                return CompletableFuture.completedFuture(true);
            }
        } catch (SQLException e) {
            
            return CompletableFuture.completedFuture(false);
        }
    }
    
    private void insertBlocks(Connection conn, long mineId, List<?> blocks, boolean isOriginal) throws SQLException {
        String insertBlock = """
            INSERT INTO nyris_blocks (mine_id, world_name, x, y, z, material, block_data, is_original)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        PreparedStatement blockStmt = conn.prepareStatement(insertBlock);
        
        for (Object block : blocks) {
            if (isOriginal && block instanceof BlockData) {
                BlockData blockData = (BlockData) block;
                String material = blockData.getBlockData().getMaterial().name();
                
                if ("AIR".equals(material)) {
                    continue;
                }
                
                blockStmt.setLong(1, mineId);
                blockStmt.setString(2, blockData.getLocation().getWorld().getName());
                blockStmt.setDouble(3, blockData.getLocation().getX());
                blockStmt.setDouble(4, blockData.getLocation().getY());
                blockStmt.setDouble(5, blockData.getLocation().getZ());
                blockStmt.setString(6, material);
                blockStmt.setString(7, blockData.getBlockData().getAsString());
                blockStmt.setBoolean(8, true);
                blockStmt.addBatch();
            } else if (!isOriginal && block instanceof org.bukkit.block.Block) {
                org.bukkit.block.Block bukkitBlock = (org.bukkit.block.Block) block;
                String material = bukkitBlock.getType().name();
                
                if ("AIR".equals(material)) {
                    continue;
                }
                
                blockStmt.setLong(1, mineId);
                blockStmt.setString(2, bukkitBlock.getWorld().getName());
                blockStmt.setDouble(3, bukkitBlock.getX());
                blockStmt.setDouble(4, bukkitBlock.getY());
                blockStmt.setDouble(5, bukkitBlock.getZ());
                blockStmt.setString(6, material);
                blockStmt.setString(7, "");
                blockStmt.setBoolean(8, false);
                blockStmt.addBatch();
            }
        }
        
        blockStmt.executeBatch();
    }
    
    @Override
    public CompletableFuture<List<MineData>> loadMines() {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM nyris_mines";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                List<MineData> mines = new ArrayList<>();
                
                while (rs.next()) {
                    UUID ownerUUID = UUID.fromString(rs.getString("owner_uuid"));
                    String worldName = rs.getString("world_name");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    int side = rs.getInt("side");
                    int height = rs.getInt("height");
                    long expirationTime = rs.getLong("expiration_time");
                    long pauseStartTime = rs.getLong("pause_start_time");
                    long remainingTime = rs.getLong("remaining_time");
                    
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;
                    
                    Location location = new Location(world, x, y, z);
                    List<BlockData> originalBlocks = loadBlocks(conn, rs.getLong("id"), true);
                    List<Block> mineBlocks = loadMineBlocks(conn, rs.getLong("id"), false);
                    
                    MineData mine = new MineData(ownerUUID, location, side, height, originalBlocks, mineBlocks, expirationTime, pauseStartTime, remainingTime);
                    mines.add(mine);
                }
                
                return CompletableFuture.completedFuture(mines);
            }
        } catch (SQLException e) {
            
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }
    
    private List<BlockData> loadBlocks(Connection conn, long mineId, boolean isOriginal) throws SQLException {
        List<BlockData> blocks = new ArrayList<>();
        String sql = "SELECT world_name, x, y, z, material, block_data FROM nyris_blocks WHERE mine_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, mineId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String worldName = rs.getString("world_name");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String materialName = rs.getString("material");
                String blockDataString = rs.getString("block_data");
                
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                
                Location location = new Location(world, x, y, z);
                
                try {
                    Material material = Material.valueOf(materialName);
                    org.bukkit.block.data.BlockData blockData = Bukkit.createBlockData(material);
                    blocks.add(new com.morreu.nyris.data.BlockData(location, blockData));
                } catch (IllegalArgumentException e) {
                    List<Material> fallbacks = Arrays.asList(
                        Material.STONE,
                        Material.COBBLESTONE,
                        Material.DIRT,
                        Material.GRASS_BLOCK
                    );
                    
                    for (Material fallback : fallbacks) {
                        try {
                            org.bukkit.block.data.BlockData blockData = Bukkit.createBlockData(fallback);
                            blocks.add(new com.morreu.nyris.data.BlockData(location, blockData));
                            break;
                        } catch (IllegalArgumentException fallbackException) {
                            continue;
                        }
                    }
                }
            }
        }
        
        return blocks;
    }
    
    private BlockData createBlockDataSafely(Location location, String material) {
        try {
            org.bukkit.block.data.BlockData bukkitBlockData = org.bukkit.Bukkit.createBlockData(material);
            return new BlockData(location, bukkitBlockData);
        } catch (IllegalArgumentException e) {
            
            
            String[] fallbackMaterials = {"STONE", "DIRT", "COBBLESTONE", "GRASS_BLOCK"};
            
            for (String fallbackMaterial : fallbackMaterials) {
                try {
                    org.bukkit.block.data.BlockData fallbackData = org.bukkit.Bukkit.createBlockData(fallbackMaterial);
                    
                    return new BlockData(location, fallbackData);
                } catch (IllegalArgumentException fallbackError) {
                    
                }
            }
            
            
            return null;
        }
    }
    
    private List<org.bukkit.block.Block> loadMineBlocks(Connection conn, long mineId, boolean isOriginal) throws SQLException {
        List<org.bukkit.block.Block> blocks = new ArrayList<>();
        String sql = "SELECT world_name, x, y, z, material, block_data FROM nyris_blocks WHERE mine_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, mineId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String worldName = rs.getString("world_name");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String materialName = rs.getString("material");
                
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                
                Location location = new Location(world, x, y, z);
                org.bukkit.block.Block block = location.getBlock();
                
                try {
                    Material material = Material.valueOf(materialName);
                    block.setType(material);
                    blocks.add(block);
                } catch (IllegalArgumentException e) {
                    List<Material> fallbacks = Arrays.asList(
                        Material.STONE,
                        Material.COBBLESTONE,
                        Material.DIRT,
                        Material.GRASS_BLOCK
                    );
                    
                    for (Material fallback : fallbacks) {
                        try {
                            block.setType(fallback);
                            blocks.add(block);
                            break;
                        } catch (IllegalArgumentException fallbackException) {
                            continue;
                        }
                    }
                }
            }
        }
        
        return blocks;
    }
    
    @Override
    public CompletableFuture<Boolean> removeMine(MineData mine) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String deleteMine = "DELETE FROM nyris_mines WHERE owner_uuid = ? AND world_name = ? AND x = ? AND y = ? AND z = ?";
                
                PreparedStatement stmt = conn.prepareStatement(deleteMine);
                stmt.setString(1, mine.getOwnerUUID().toString());
                stmt.setString(2, mine.getLocation().getWorld().getName());
                stmt.setDouble(3, mine.getLocation().getX());
                stmt.setDouble(4, mine.getLocation().getY());
                stmt.setDouble(5, mine.getLocation().getZ());
                
                int affected = stmt.executeUpdate();
                return affected > 0;
                
            } catch (SQLException e) {
                
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> updateMine(MineData mine) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE nyris_mines SET world_name = ?, x = ?, y = ?, z = ?, side = ?, height = ?, expiration_time = ?, pause_start_time = ?, remaining_time = ? WHERE owner_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mine.getLocation().getWorld().getName());
                stmt.setDouble(2, mine.getLocation().getX());
                stmt.setDouble(3, mine.getLocation().getY());
                stmt.setDouble(4, mine.getLocation().getZ());
                stmt.setInt(5, mine.getSide());
                stmt.setInt(6, mine.getHeight());
                stmt.setLong(7, mine.getExpirationTime());
                stmt.setLong(8, mine.getPauseStartTime());
                stmt.setLong(9, mine.getRemainingTime());
                stmt.setString(10, mine.getOwnerUUID().toString());
                
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    return CompletableFuture.completedFuture(false);
                }
                
                String deleteBlocksSql = "DELETE FROM nyris_blocks WHERE mine_id = (SELECT id FROM nyris_mines WHERE owner_uuid = ?)";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteBlocksSql)) {
                    deleteStmt.setString(1, mine.getOwnerUUID().toString());
                    deleteStmt.executeUpdate();
                }
                
                String selectMineIdSql = "SELECT id FROM nyris_mines WHERE owner_uuid = ?";
                long mineId;
                try (PreparedStatement selectStmt = conn.prepareStatement(selectMineIdSql)) {
                    selectStmt.setString(1, mine.getOwnerUUID().toString());
                    ResultSet rs = selectStmt.executeQuery();
                    if (rs.next()) {
                        mineId = rs.getLong("id");
                    } else {
                        return CompletableFuture.completedFuture(false);
                    }
                }
                
                for (BlockData blockData : mine.getOriginalBlocks()) {
                    String blockSql = "INSERT INTO nyris_blocks (mine_id, world_name, x, y, z, material, block_data) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement blockStmt = conn.prepareStatement(blockSql)) {
                        blockStmt.setLong(1, mineId);
                        blockStmt.setString(2, blockData.getLocation().getWorld().getName());
                        blockStmt.setDouble(3, blockData.getLocation().getX());
                        blockStmt.setDouble(4, blockData.getLocation().getY());
                        blockStmt.setDouble(5, blockData.getLocation().getZ());
                        blockStmt.setString(6, blockData.getBlockData().getMaterial().name());
                        blockStmt.setString(7, blockData.getBlockData().getAsString());
                        blockStmt.executeUpdate();
                    }
                }
                
                for (Block block : mine.getMineBlocks()) {
                    if (block.getType() != Material.AIR) {
                        String blockSql = "INSERT INTO nyris_blocks (mine_id, world_name, x, y, z, material, block_data) VALUES (?, ?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement blockStmt = conn.prepareStatement(blockSql)) {
                            blockStmt.setLong(1, mineId);
                            blockStmt.setString(2, block.getWorld().getName());
                            blockStmt.setDouble(3, block.getX());
                            blockStmt.setDouble(4, block.getY());
                            blockStmt.setDouble(5, block.getZ());
                            blockStmt.setString(6, block.getType().name());
                            blockStmt.setString(7, block.getBlockData().getAsString());
                            blockStmt.executeUpdate();
                        }
                    }
                }
                
                return CompletableFuture.completedFuture(true);
            }
        } catch (SQLException e) {
            
            return CompletableFuture.completedFuture(false);
        }
    }
    
    @Override
    public CompletableFuture<Boolean> isConnected() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return conn.isValid(5);
            } catch (SQLException e) {
                return false;
            }
        });
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
