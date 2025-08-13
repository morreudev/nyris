package com.morreu.nyris;

import com.morreu.nyris.data.BlockData;
import com.morreu.nyris.data.MineData;
import com.morreu.nyris.manager.DataManager;
import com.morreu.nyris.manager.JSONDataManager;
import com.morreu.nyris.mysql.MySQLDataManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Nyris extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<UUID, MineData> playerMines;
    private Map<Location, MineData> locationMines;
    private DataManager dataManager;
    
    private Map<String, List<Material>> oreTypesCache;
    private long lastCacheClear;
    private static final long CACHE_CLEAR_INTERVAL = 300000;
    
    private Map<UUID, Integer> brokenBlocksCount;
    private Map<UUID, List<Integer>> expirationTaskIds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        
        playerMines = new ConcurrentHashMap<>();
        locationMines = new ConcurrentHashMap<>();
        
        oreTypesCache = new ConcurrentHashMap<>();
        lastCacheClear = System.currentTimeMillis();
        
        brokenBlocksCount = new ConcurrentHashMap<>();
        expirationTaskIds = new ConcurrentHashMap<>();
        
        initializeDataManager();
        loadMines();
        
        getServer().getPluginManager().registerEvents(this, this);
        
        getCommand("nyris").setExecutor(this);

        Bukkit.getConsoleSender().sendMessage("§a[Nyris] plugin iniciado com sucesso!");
        

    }
    
    private void initializeDataManager() {
        String dbType = config.getString("database.type", "mysql").toLowerCase();
        
        try {
            if ("mysql".equals(dbType)) {
                dataManager = new MySQLDataManager(this, config);
                
                dataManager.isConnected().thenAccept(connected -> {
                    if (connected) {
                        
                    } else {
                        dataManager = new JSONDataManager(this, config);
                    }
                });
                
            } else {
                dataManager = new JSONDataManager(this, config);
            }
            
        } catch (Exception e) {
            dataManager = new JSONDataManager(this, config);
        }
    }
    
    private void saveMines() {
        if (dataManager != null) {
            for (MineData mine : playerMines.values()) {
                            dataManager.saveMine(mine);
            }
        }
    }
    
    private void loadMines() {
        if (dataManager != null) {
            dataManager.loadMines().thenAccept(mines -> {
                if (mines != null) {
                            Map<UUID, MineData> latestMines = new HashMap<>();
        
        for (MineData mine : mines) {
                        try {
                            if (mine != null && mine.getLocation().getWorld() != null) {
                                UUID ownerUUID = mine.getOwnerUUID();
                                
                                if (latestMines.containsKey(ownerUUID)) {
                                    MineData existingMine = latestMines.get(ownerUUID);
                                    
                                    if (mine.getExpirationTime() > existingMine.getExpirationTime()) {
                                                    latestMines.put(ownerUUID, mine);
        }
                                } else {
                                    latestMines.put(ownerUUID, mine);
                                }
                            }
                        } catch (Exception e) {
                            
                        }
                    }
                    
                    int loadedCount = 0;
                    for (MineData mine : latestMines.values()) {
                        try {
                            playerMines.put(mine.getOwnerUUID(), mine);
                            locationMines.put(mine.getLocation(), mine);
                            
                            brokenBlocksCount.put(mine.getOwnerUUID(), 0);
                            
                            long currentTime = System.currentTimeMillis();
                            long adjustedExpirationTime = mine.getAdjustedExpirationTime();
                            
                            if (adjustedExpirationTime > currentTime) {
                                scheduleMineExpiration(mine, config.getInt("mine.duration.warning_time", 300));
                                loadedCount++;
                            } else {
                                
                            }
                        } catch (Exception e) {
                            
                        }
                    }
                    
                    cleanupDuplicateMines(mines, latestMines);
                }
            }).exceptionally(throwable -> {
                
                return null;
            });
        }
    }
    
    private void cleanupDuplicateMines(List<MineData> allMines, Map<UUID, MineData> latestMines) {
        if (dataManager == null) return;
        
        List<MineData> minesToRemove = new ArrayList<>();
        
        for (MineData mine : allMines) {
            if (mine != null && latestMines.containsKey(mine.getOwnerUUID())) {
                MineData latestMine = latestMines.get(mine.getOwnerUUID());
                
                if (!mine.getLocation().equals(latestMine.getLocation()) || 
                    mine.getExpirationTime() != latestMine.getExpirationTime()) {
                    minesToRemove.add(mine);
                }
            }
        }
        
        if (!minesToRemove.isEmpty()) {
            for (MineData duplicateMine : minesToRemove) {
                dataManager.removeMine(duplicateMine);
            }
        }
    }

    @Override
    public void onDisable() {
        cancelAllExpirationTasks();
        
        long pauseTime = System.currentTimeMillis();
        pauseAllMines(pauseTime);
        
        saveMinesSync();
        
        if (dataManager != null) {
            dataManager.close();
        }
    }
    
    private void pauseAllMines(long pauseTime) {
        for (MineData mine : playerMines.values()) {
            long timeRemaining = mine.getExpirationTime() - System.currentTimeMillis();
            mine.setPauseStartTime(pauseTime);
            mine.setRemainingTime(timeRemaining);
        }
    }
    
    private void saveMinesSync() {
        if (dataManager != null) {
            for (MineData mine : playerMines.values()) {
                try {
                    Boolean success = dataManager.saveMine(mine).get();
                } catch (Exception e) {
                    
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando só pode ser usado por jogadores!");
            return true;
        }

        Player player = (Player) sender;
        
        if (command.getName().equalsIgnoreCase("nyris")) {
                    if (args.length > 0) {
            if (args[0].equalsIgnoreCase("limpar")) {
                if (player.hasPermission("nyris.admin")) {
                    clearAllMines(player);
                } else {
                    player.sendMessage("§cVocê não tem permissão para usar este comando!");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("duplicadas")) {
                if (player.hasPermission("nyris.admin")) {
                    cleanupDuplicateMinesCommand(player);
                } else {
                    player.sendMessage("§cVocê não tem permissão para usar este comando!");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("status")) {
                showMineStatus(player);
                return true;
            } else if (args[0].equalsIgnoreCase("teste")) {
                if (player.hasPermission("nyris.admin")) {
                    testRegeneration(player);
                } else {
                    player.sendMessage("§cVocê não tem permissão para usar este comando!");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("forcar")) {
                if (player.hasPermission("nyris.admin")) {
                    forceRegeneration(player);
                } else {
                    player.sendMessage("§cVocê não tem permissão para usar este comando!");
                }
                return true;
            }
        }
            
            giveMineItem(player);
            player.sendMessage("§aVocê recebeu o item especial da mina!");
            return true;
        }
        
        return false;
    }

    private void giveMineItem(Player player) {
        ItemStack mineItem = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = mineItem.getItemMeta();
        meta.setDisplayName("§6§lMina Personalizada");
        meta.setLore(Arrays.asList(
            "§7Clique com botão direito para",
            "§7criar uma mina personalizada!",
            "",
            "§eJogador: " + player.getName()
        ));
        mineItem.setItemMeta(meta);
        
        player.getInventory().addItem(mineItem);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }
        
        if (item.getItemMeta().getDisplayName().equals("§6§lMina Personalizada")) {
            event.setCancelled(true);
            
            if (event.getAction().toString().contains("RIGHT_CLICK")) {
                Location targetLocation = getTargetLocation(player);
                if (targetLocation != null) {
                    createMine(player, targetLocation);
                } else {
                    player.sendMessage("§cNão foi possível encontrar uma localização válida!");
                }
            }
        }
    }

    private Location getTargetLocation(Player player) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();
        
        for (int i = 2; i < 100; i++) {
            Location loc = eyeLocation.clone().add(direction.clone().multiply(i));
            if (loc.getBlock().getType() != Material.AIR) {
                Location safeLocation = loc.clone().add(0, -1, 0);
                
                if (isLocationSafe(player, safeLocation)) {
                    return safeLocation;
                }
            }
        }
        return null;
    }

    private boolean isLocationSafe(Player player, Location location) {
        Location playerLocation = player.getLocation();
        
        double minDistance = 2.0;
        if (location.distance(playerLocation) < minDistance) {
            return false;
        }
        
        if (location.getY() > playerLocation.getY() + 20) {
            return false;
        }
        
        if (location.getY() < playerLocation.getY() - 50) {
            return false;
        }
        
        int side = config.getInt("mine.dimensions.side", 10);
        int height = config.getInt("mine.dimensions.height", 5);
        
        return !willMineCollideWithPlayer(player, location, side, height);
    }

    private boolean willMineCollideWithPlayer(Player player, Location mineLocation, int side, int height) {
        Location playerLocation = player.getLocation();
        
        double minDistance = 1.5;
        
        Location corner1 = mineLocation.clone();
        if (corner1.distance(playerLocation) < minDistance) return true;
        
        Location corner2 = mineLocation.clone().add(side - 1, 0, side - 1);
        if (corner2.distance(playerLocation) < minDistance) return true;
        
        Location corner3 = mineLocation.clone().add(0, height - 1, 0);
        if (corner3.distance(playerLocation) < minDistance) return true;
        
        Location corner4 = mineLocation.clone().add(side - 1, height - 1, side - 1);
        if (corner4.distance(playerLocation) < minDistance) return true;
        
        return isLocationInMine(playerLocation, mineLocation, side, height);
    }
    
    private boolean isLocationInMine(Location location, Location mineLocation, int side, int height) {
        return location.getWorld().equals(mineLocation.getWorld()) &&
               location.getBlockX() >= mineLocation.getBlockX() &&
               location.getBlockX() < mineLocation.getBlockX() + side &&
               location.getBlockZ() >= mineLocation.getBlockZ() &&
               location.getBlockZ() < mineLocation.getBlockZ() + side &&
               location.getBlockY() >= mineLocation.getBlockY() &&
               location.getBlockY() < mineLocation.getBlockY() + height;
    }

    private void createMine(Player player, Location center) {
        if (!isPlayerAllowedToCreateMine(player)) {
            return;
        }
        
        if (!isLocationValidForMine(player, center)) {
            return;
        }

        int side = config.getInt("mine.dimensions.side", 10);
        int height = config.getInt("mine.dimensions.height", 5);
        int activeTime = config.getInt("mine.duration.active_time", 3600);
        int warningTime = config.getInt("mine.duration.warning_time", 300);
        
        if (activeTime <= 0) {
            player.sendMessage("§cErro: Tempo de expiração inválido na configuração!");
            return;
        }
        
        Location mineLocation = center.clone();
        
        List<Block> mineBlocks = new ArrayList<>();
        List<BlockData> originalBlocks = new ArrayList<>();
        List<Material> oreTypes = getOreTypes();
        Random random = new Random();
        
        int batchSize = 10;
        int totalBlocks = side * side * height;
        int processedBlocks = 0;
        
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                for (int y = 0; y < height; y++) {
                    Location loc = mineLocation.clone().add(x, y, z);
                    Block block = loc.getBlock();
                    
                    if (block.getType() == Material.AIR) {
                        Material oreType = oreTypes.get(random.nextInt(oreTypes.size()));
                        
                                            mineBlocks.add(block);
                    originalBlocks.add(new BlockData(loc, Material.AIR.createBlockData()));
                    
                    processedBlocks++;
                    
                    block.setType(oreType);
                    }
                }
            }
        }
        
        MineData mineData = new MineData(
            player.getUniqueId(),
            mineLocation,
            side,
            height,
            originalBlocks,
            mineBlocks,
            System.currentTimeMillis() + (activeTime * 1000L)
        );
        
        playerMines.put(player.getUniqueId(), mineData);
        locationMines.put(mineLocation, mineData);
        
        brokenBlocksCount.put(player.getUniqueId(), 0);
        
        playCreationEffects(player, mineLocation);
        
        player.sendMessage("§aMina criada com sucesso!");
        
        scheduleMineExpiration(mineData, warningTime);
        
        saveMines();
    }
    

    
    private boolean isPlayerAllowedToCreateMine(Player player) {
        int maxMinesPerPlayer = config.getInt("mine.limits.max_per_player", 1);
        UUID playerUUID = player.getUniqueId();
        
        if (playerMines.containsKey(playerUUID)) {
            MineData existingMine = playerMines.get(playerUUID);
            
            if (existingMine == null || existingMine.getLocation() == null || existingMine.getLocation().getWorld() == null) {
                playerMines.remove(playerUUID);
                locationMines.remove(existingMine != null ? existingMine.getLocation() : null);
                cancelExpirationTasks(playerUUID);
                brokenBlocksCount.remove(playerUUID);
                return true;
            }
            
            long currentTime = System.currentTimeMillis();
            long timeRemaining = existingMine.getAdjustedExpirationTime() - currentTime;
            
            if (timeRemaining > 0) {
                String timeFormatted = formatTime((int)(timeRemaining / 1000));
                player.sendMessage("§cVocê já possui uma mina ativa que expira em " + timeFormatted + "!");
                return false;
            } else {
                regenerateMine(existingMine);
            }
        }
        
        int maxGlobalMines = config.getInt("mine.limits.max_global", 100);
        if (playerMines.size() >= maxGlobalMines) {
            player.sendMessage("§cLimite global de minas atingido! Tente novamente mais tarde.");
            return false;
        }
        
        return true;
    }
    
    private boolean isLocationValidForMine(Player player, Location center) {
        if (!player.hasPermission("nyris.create")) {
            player.sendMessage("§cVocê não tem permissão para criar minas!");
            return false;
        }
        
        if (!isWorldAllowed(center.getWorld())) {
            player.sendMessage("§cMinas não são permitidas neste mundo!");
            return false;
        }
        
        if (!isLocationWithinWorldBounds(center)) {
            player.sendMessage("§cLocalização muito próxima dos limites do mundo!");
            return false;
        }
        
        if (!isLocationSafe(player, center)) {
            player.sendMessage("§cLocalização muito próxima! Mire mais longe.");
            return false;
        }
        
        return true;
    }
    
    private boolean isWorldAllowed(org.bukkit.World world) {
        List<String> allowedWorlds = config.getStringList("mine.allowed_worlds");
        if (allowedWorlds.isEmpty()) {
            return true;
        }
        return allowedWorlds.contains(world.getName());
    }
    
    private boolean isLocationWithinWorldBounds(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return false;
        
        double borderSize = world.getWorldBorder().getSize();
        if (borderSize > 0) {
            Location center = world.getWorldBorder().getCenter();
            double distance = location.distance(center);
            if (distance > (borderSize / 2) - 50) {
                return false;
            }
        }
        
        return true;
    }

    private void playCreationEffects(Player player, Location location) {
        if (config.getBoolean("creation_effects.sound.enabled", true)) {
            try {
                Sound sound = Sound.valueOf(config.getString("creation_effects.sound.type", "ENTITY_PLAYER_LEVELUP"));
                float volume = (float) config.getDouble("creation_effects.sound.volume", 1.0);
                float pitch = (float) config.getDouble("creation_effects.sound.pitch", 1.0);
                player.playSound(location, sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                
            }
        }
        
        if (config.getBoolean("creation_effects.title.enabled", true)) {
            String mainTitle = config.getString("creation_effects.title.main_title", "§6§lMINA COLOCADA");
            String subtitle = config.getString("creation_effects.title.subtitle", "§fSua mina foi criada com sucesso!");
            
            player.sendTitle(mainTitle, subtitle, 10, 40, 10);
        }
        
        if (config.getBoolean("creation_effects.particles.enabled", true)) {
            try {
                Particle particle = Particle.valueOf(config.getString("creation_effects.particles.type", "PORTAL"));
                int count = config.getInt("creation_effects.particles.count", 100);
                double offsetX = config.getDouble("creation_effects.particles.offset_x", 0.5);
                double offsetY = config.getDouble("creation_effects.particles.offset_y", 0.5);
                double offsetZ = config.getDouble("creation_effects.particles.offset_z", 0.5);
                
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ);
            } catch (IllegalArgumentException e) {
                
            }
        }
    }

    private List<Material> getOreTypes() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheClear > CACHE_CLEAR_INTERVAL) {
            oreTypesCache.clear();
            lastCacheClear = currentTime;
        }
        
        String cacheKey = "ore_types";
        if (oreTypesCache.containsKey(cacheKey)) {
            return new ArrayList<>(oreTypesCache.get(cacheKey));
        }
        
        List<String> oreNames = config.getStringList("mine.ore_types");
        List<Material> oreTypes = new ArrayList<>();
        
        for (String oreName : oreNames) {
            try {
                Material material = Material.valueOf(oreName.toUpperCase());
                oreTypes.add(material);
            } catch (IllegalArgumentException e) {
                
            }
        }
        
        if (oreTypes.isEmpty()) {
            oreTypes.addAll(Arrays.asList(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.DIAMOND_ORE,
                Material.EMERALD_ORE,
                Material.LAPIS_ORE,
                Material.REDSTONE_ORE
            ));
        }
        
        oreTypesCache.put(cacheKey, new ArrayList<>(oreTypes));
        
        return oreTypes;
    }

    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " segundos";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            return minutes + " minuto" + (minutes > 1 ? "s" : "");
        } else if (seconds < 86400) {
            int hours = seconds / 3600;
            return hours + " hora" + (hours > 1 ? "s" : "");
        } else {
            int days = seconds / 86400;
            return days + " dia" + (days > 1 ? "s" : "");
        }
    }

    private String formatWarningTime(int seconds) {
        if (seconds < 60) {
            return seconds + " segundo" + (seconds > 1 ? "s" : "");
        } else {
            int minutes = seconds / 60;
            return minutes + " minuto" + (minutes > 1 ? "s" : "");
        }
    }

    private void scheduleMineExpiration(MineData mine, int warningTime) {
        long currentTime = System.currentTimeMillis();
        long adjustedExpirationTime = mine.getAdjustedExpirationTime();
        long timeUntilExpiration = adjustedExpirationTime - currentTime;
        long timeUntilWarning = timeUntilExpiration - (warningTime * 1000L);
        
        UUID ownerUUID = mine.getOwnerUUID();
        
        cancelExpirationTasks(ownerUUID);
        
        if (!playerMines.containsKey(ownerUUID)) {
            return;
        }
        
        if (timeUntilWarning > 0) {
            int warningTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!playerMines.containsKey(ownerUUID)) {
                    return;
                }
                
                Player player = Bukkit.getPlayer(ownerUUID);
                if (player != null && player.isOnline()) {
                    String warningFormatted = formatWarningTime(warningTime);
                    player.sendMessage("§eAviso: Sua mina expira em " + warningFormatted + "!");
                }
            }, timeUntilWarning / 50L).getTaskId();
            
            if (!expirationTaskIds.containsKey(ownerUUID)) {
                expirationTaskIds.put(ownerUUID, new ArrayList<>());
            }
            expirationTaskIds.get(ownerUUID).add(warningTaskId);
        }
        
        startCountdown(mine, warningTime);
        
        int expirationTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!playerMines.containsKey(ownerUUID)) {
                return;
            }
            
            Player player = Bukkit.getPlayer(ownerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage("§cSua mina expirou e foi removida automaticamente!");
                playExpirationTitle(player);
                playFinalSound(player);
            }
            
            regenerateMine(mine);
        }, timeUntilExpiration / 50L).getTaskId();
        
        if (!expirationTaskIds.containsKey(ownerUUID)) {
            expirationTaskIds.put(ownerUUID, new ArrayList<>());
        }
        expirationTaskIds.get(ownerUUID).add(expirationTaskId);

    }
    
    private void startCountdown(MineData mine, int warningTime) {
        long currentTime = System.currentTimeMillis();
        long adjustedExpirationTime = mine.getAdjustedExpirationTime();
        long timeUntilCountdown = adjustedExpirationTime - currentTime - (warningTime * 1000L);
        
        if (timeUntilCountdown <= 0) {
            return;
        }
        
        UUID ownerUUID = mine.getOwnerUUID();
        
        BukkitRunnable countdownTask = new BukkitRunnable() {
            int countdown = warningTime;
            
            @Override
            public void run() {
                if (countdown <= 0) {
                    this.cancel();
                    return;
                }
                
                if (!playerMines.containsKey(ownerUUID)) {
                    this.cancel();
                    return;
                }
                
                Player player = Bukkit.getPlayer(ownerUUID);
                if (player != null && player.isOnline()) {
                    if (countdown <= 3) {
                        String message = countdown == 1 ? "segundo" : "segundos";
                        player.sendMessage("§cSua mina expira em " + countdown + " " + message + "!");
                        playCountdownSound(player);
                    }
                }
                
                countdown--;
            }
        };
        
        countdownTask.runTaskTimer(this, timeUntilCountdown / 50L, 20L);
        
        if (!expirationTaskIds.containsKey(ownerUUID)) {
            expirationTaskIds.put(ownerUUID, new ArrayList<>());
        }
        expirationTaskIds.get(ownerUUID).add(countdownTask.getTaskId());
    }
    
    private void cancelExpirationTasks(UUID ownerUUID) {
        List<Integer> taskIds = expirationTaskIds.get(ownerUUID);
        if (taskIds != null && !taskIds.isEmpty()) {
            for (Integer taskId : taskIds) {
                try {
                    Bukkit.getScheduler().cancelTask(taskId);
                } catch (Exception e) {
                    
                }
            }
            expirationTaskIds.remove(ownerUUID);
        }
    }
    
    private void forceCancelAllTasksForPlayer(UUID ownerUUID) {
        cancelExpirationTasks(ownerUUID);
        
        for (Map.Entry<UUID, List<Integer>> entry : new HashMap<>(expirationTaskIds).entrySet()) {
            if (entry.getKey().equals(ownerUUID)) {
                List<Integer> taskIds = entry.getValue();
                if (taskIds != null) {
                    for (Integer taskId : taskIds) {
                        try {
                            Bukkit.getScheduler().cancelTask(taskId);
                        } catch (Exception e) {
                            
                        }
                    }
                }
                expirationTaskIds.remove(entry.getKey());
            }
        }
    }
    
    private void sendActionBar(Player player, String message) {
        player.sendMessage(message);
    }
    
    private void cancelAllExpirationTasks() {
        for (List<Integer> taskIds : expirationTaskIds.values()) {
            if (taskIds != null) {
                for (Integer taskId : taskIds) {
                    try {
                        Bukkit.getScheduler().cancelTask(taskId);
                    } catch (Exception e) {
                        
                    }
                }
            }
        }
        expirationTaskIds.clear();
    }
    
    private void playCountdownSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }
    
    private void playFinalSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }
    
    private void playExpirationTitle(Player player) {
        if (config.getBoolean("expiration_effects.title.enabled", true)) {
            String mainTitle = config.getString("expiration_effects.title.main_title", "§c§lMINA EXPIRADA");
            String subtitle = config.getString("expiration_effects.title.subtitle", "§fSua mina foi removida automaticamente");
            
            player.sendTitle(mainTitle, subtitle, 10, 40, 10);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();
        
        MineData mine = findMineByLocation(blockLocation);
        if (mine != null) {
            if (!mine.getOwnerUUID().equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage("§cVocê não pode minerar nesta mina! Ela pertence a outro jogador.");
                return;
            }
            
            mine.removeBlock(event.getBlock());
            
                    UUID ownerUUID = mine.getOwnerUUID();
        int currentBroken = brokenBlocksCount.getOrDefault(ownerUUID, 0);
        brokenBlocksCount.put(ownerUUID, currentBroken + 1);
            
            if (shouldTriggerRegeneration(mine)) {
                scheduleAutoRegeneration(mine, player);
            }
            
            if (mine.isEmpty()) {
                player.sendMessage("§aMina esgotada! Regenerando em 30 segundos...");
                player.sendMessage("§7O mundo será restaurado ao estado anterior.");
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        regenerateMine(mine);
                        player.sendMessage("§aMina regenerada! Mundo restaurado.");
                    }
                }.runTaskLater(this, 20 * 30);
            }
        }
    }

    private MineData findMineByLocation(Location location) {
        for (MineData mine : locationMines.values()) {
            if (isLocationInMine(location, mine)) {
                return mine;
            }
        }
        return null;
    }

    private boolean isLocationInMine(Location location, MineData mine) {
        Location mineLoc = mine.getLocation();
        int side = mine.getSide();
        int height = mine.getHeight();
        
        return location.getWorld().equals(mineLoc.getWorld()) &&
               location.getBlockX() >= mineLoc.getBlockX() &&
               location.getBlockX() < mineLoc.getBlockX() + side &&
               location.getBlockZ() >= mineLoc.getBlockZ() &&
               location.getBlockZ() < mineLoc.getBlockZ() + side &&
               location.getBlockY() >= mineLoc.getBlockY() &&
               location.getBlockY() < mineLoc.getBlockY() + height;
    }
    
    private boolean shouldTriggerRegeneration(MineData mine) {

        if (!config.getBoolean("mine.auto_regeneration.enabled", true)) {
            return false;
        }
        
        UUID ownerUUID = mine.getOwnerUUID();
        int brokenBlocks = brokenBlocksCount.getOrDefault(ownerUUID, 0);
        

        int totalBlocks = mine.getMineBlocks().size() + brokenBlocks;
        
        if (totalBlocks == 0) return false;
        

        double brokenPercentage = (double) brokenBlocks / totalBlocks * 100;
        double threshold = config.getDouble("mine.auto_regeneration.threshold_percentage", 30.0);
        
        if (brokenPercentage >= threshold) {
            return true;
        }
        
        return false;
    }
    
    private void scheduleAutoRegeneration(MineData mine, Player player) {
        UUID ownerUUID = mine.getOwnerUUID();
        
        if (player.hasMetadata("nyris_regenerating")) {
            return;
        }
        
        player.setMetadata("nyris_regenerating", new FixedMetadataValue(this, true));
        
        sendActionBar(player, "§6Regenerando mina...");
        player.sendTitle("§6§lREGENERAÇÃO", "§fSua mina será regenerada em 3 segundos...", 10, 40, 10);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                regenerateMineBlocks(mine, player);
            }
        }.runTaskLater(this, 20 * 3);
    }
    
    private void regenerateMineBlocks(MineData mine, Player player) {
        UUID ownerUUID = mine.getOwnerUUID();
        
        int brokenBlocks = brokenBlocksCount.getOrDefault(ownerUUID, 0);
        
        if (brokenBlocks == 0) {
            player.sendMessage("§aNenhum bloco para regenerar!");
            player.removeMetadata("nyris_regenerating", Nyris.this);
            return;
        }
        
        Location safeLocation = findSafeLocationOutsideMine(mine, player);
        if (safeLocation != null) {
            player.teleport(safeLocation);
            player.sendMessage("§eVocê foi teleportado para o topo da mina para regeneração segura!");
        }
        
        regenerateCompleteMine(mine, player);
    }
    
    private Location findSafeLocationOutsideMine(MineData mine, Player player) {
        Location mineCenter = mine.getLocation().clone().add(mine.getSide() / 2.0, mine.getHeight() / 2.0, mine.getSide() / 2.0);
        
        Location topLocation = mineCenter.clone().add(0, mine.getHeight() + 2, 0);
        
        if (isLocationSafeForTeleport(topLocation, mine)) {
            return topLocation;
        }
        
        for (int y = mine.getHeight() + 2; y <= mine.getHeight() + 10; y++) {
            Location testLocation = mineCenter.clone().add(0, y, 0);
            if (isLocationSafeForTeleport(testLocation, mine)) {
                return testLocation;
            }
        }
        
        return player.getLocation();
    }
    
    private Location findFallbackLocation(MineData mine, Player player) {
        Location mineLoc = mine.getLocation();
        double maxDistance = Math.max(mine.getSide(), mine.getHeight()) + 10;
        
        for (int angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(angle);
            double x = Math.cos(radians) * maxDistance;
            double z = Math.sin(radians) * maxDistance;
            
            Location testLocation = mineLoc.clone().add(x, 0, z);
            
            for (int y = -5; y <= 10; y++) {
                Location heightTest = testLocation.clone().add(0, y, 0);
                if (isLocationSafeForTeleport(heightTest, mine)) {
                    return heightTest;
                }
            }
        }
        
        return null;
    }
    
    private boolean isLocationSafeForTeleport(Location location, MineData mine) {
        if (isLocationInMine(location, mine)) {
            return false;
        }
        
        if (!location.getChunk().isLoaded()) {
            return false;
        }
        
        org.bukkit.block.Block block = location.getBlock();
        if (block.getType().isSolid()) {
            return false;
        }
        
        Location above1 = location.clone().add(0, 1, 0);
        Location above2 = location.clone().add(0, 2, 0);
        Location below = location.clone().add(0, -1, 0);
        
        boolean hasSpace = !above1.getBlock().getType().isSolid() && 
                          !above2.getBlock().getType().isSolid() && 
                          below.getBlock().getType().isSolid();
        
        boolean noLiquids = block.getType() != Material.LAVA && 
                           block.getType() != Material.WATER &&
                           above1.getBlock().getType() != Material.LAVA &&
                           above1.getBlock().getType() != Material.WATER;
        
        return hasSpace && noLiquids;
    }
    
    private void regenerateCompleteMine(MineData mine, Player player) {
        UUID ownerUUID = mine.getOwnerUUID();
        Location mineLoc = mine.getLocation();
        int side = mine.getSide();
        int height = mine.getHeight();
        
        player.sendMessage("§6Regenerando mina completamente...");
        
        mine.getMineBlocks().clear();
        
        int totalBlocks = 0;
        List<Material> oreTypes = getOreTypes();
        Random random = new Random();
        
        Location center = mineLoc.clone().add(side / 2.0, height / 2.0, side / 2.0);
        center.getWorld().spawnParticle(Particle.PORTAL, center, 50, 2, 2, 2);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < side; x++) {
                for (int z = 0; z < side; z++) {
                    Location blockLoc = mineLoc.clone().add(x, y, z);
                    org.bukkit.block.Block block = blockLoc.getBlock();
                    
                    if (block != null && block.getChunk().isLoaded()) {
                        Material oreType = oreTypes.get(random.nextInt(oreTypes.size()));
                        
                        block.setType(oreType);
                        
                        mine.getMineBlocks().add(block);
                        totalBlocks++;
                        
                        if (totalBlocks % 10 == 0) {
                            blockLoc.getWorld().spawnParticle(Particle.PORTAL, blockLoc.clone().add(0.5, 0.5, 0.5), 2, 0.1, 0.1, 0.1);
                        }
                    }
                }
            }
            
            if (y < height - 1) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        center.getWorld().spawnParticle(Particle.PORTAL, center, 100, 2, 2, 2);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        brokenBlocksCount.put(ownerUUID, 0);
        
        player.sendMessage("§aMina resetada com sucesso! " + totalBlocks + " blocos regenerados.");
        player.sendTitle("§a§lREGENERAÇÃO CONCLUÍDA", "§f" + totalBlocks + " blocos foram regenerados!", 10, 60, 10);
        
        player.removeMetadata("nyris_regenerating", Nyris.this);
        
        saveMines();
    }
    
    private List<Location> findAirBlocksInMine(MineData mine) {
        List<Location> airBlocks = new ArrayList<>();
        Location mineLoc = mine.getLocation();
        int side = mine.getSide();
        int height = mine.getHeight();
        
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                for (int y = 0; y < height; y++) {
                    Location blockLoc = mineLoc.clone().add(x, y, z);
                    org.bukkit.block.Block block = blockLoc.getBlock();
                    
                    if (block != null && block.getChunk().isLoaded() && block.getType() == Material.AIR) {
                        airBlocks.add(blockLoc);
                    }
                }
            }
        }
        
        return airBlocks;
    }

    private void regenerateMine(MineData mine) {
        try {
            UUID ownerUUID = mine.getOwnerUUID();
            
            forceCancelAllTasksForPlayer(ownerUUID);
            
            for (org.bukkit.block.Block mineBlock : mine.getMineBlocks()) {
                if (mineBlock != null && mineBlock.getWorld() != null && mineBlock.getChunk().isLoaded()) {
                    try {
                        mineBlock.setType(Material.AIR);
                    } catch (Exception e) {
                        
                    }
                }
            }
            
            for (BlockData blockData : mine.getOriginalBlocks()) {
                if (blockData != null && blockData.getLocation() != null && blockData.getLocation().getWorld() != null) {
                    try {
                        org.bukkit.block.Block block = blockData.getLocation().getBlock();
                        if (block.getChunk().isLoaded()) {
                            block.setBlockData(blockData.getBlockData());
                        }
                    } catch (Exception e) {
                        
                    }
                }
            }
            
            playerMines.remove(ownerUUID);
            locationMines.remove(mine.getLocation());
            
            brokenBlocksCount.remove(ownerUUID);
            
            if (dataManager != null) {
                dataManager.removeMine(mine).thenAccept(success -> {
                    
                });
            }
            
        } catch (Exception e) {
            UUID ownerUUID = mine.getOwnerUUID();
            forceCancelAllTasksForPlayer(ownerUUID);
            playerMines.remove(ownerUUID);
            locationMines.remove(mine.getLocation());
            brokenBlocksCount.remove(ownerUUID);
        }
    }
    
    private void showMineStatus(Player player) {
        UUID playerUUID = player.getUniqueId();
        MineData mine = playerMines.get(playerUUID);
        
        if (mine == null) {
            player.sendMessage("§eVocê não possui uma mina ativa.");
            return;
        }
        
        int totalBlocks = mine.getMineBlocks().size();
        int brokenBlocks = brokenBlocksCount.getOrDefault(playerUUID, 0);
        int remainingBlocks = totalBlocks;
        
        if (totalBlocks > 0) {
            double brokenPercentage = (double) brokenBlocks / (totalBlocks + brokenBlocks) * 100;
            double threshold = config.getDouble("mine.auto_regeneration.threshold_percentage", 30.0);
            
            player.sendMessage("§6=== Status da Mina ===");
            player.sendMessage("§7Localização: §f" + formatLocation(mine.getLocation()));
            player.sendMessage("§7Dimensões: §f" + mine.getSide() + "x" + mine.getSide() + "x" + mine.getHeight());
            player.sendMessage("§7Blocos restantes: §f" + remainingBlocks);
            player.sendMessage("§7Blocos quebrados: §f" + brokenBlocks);
            player.sendMessage("§7Porcentagem quebrada: §f" + String.format("%.1f", brokenPercentage) + "%");
            player.sendMessage("§7Threshold de regeneração: §f" + threshold + "%");
            
            if (brokenPercentage >= threshold) {
                player.sendMessage("§aRegeneração automática disponível!");
            } else {
                int blocksUntilRegen = (int) Math.ceil((threshold - brokenPercentage) / 100 * (totalBlocks + brokenBlocks));
                player.sendMessage("§eFaltam " + blocksUntilRegen + " blocos para regeneração automática");
            }
            
                    long currentTime = System.currentTimeMillis();
        long timeRemaining = mine.getAdjustedExpirationTime() - currentTime;
        if (timeRemaining > 0) {
            player.sendMessage("§7Tempo restante: §f" + formatTime((int)(timeRemaining / 1000)));
        }
        } else {
            player.sendMessage("§eSua mina não possui blocos ativos.");
        }
    }
    
    private String formatLocation(Location location) {
        return "X: " + location.getBlockX() + ", Y: " + location.getBlockY() + ", Z: " + location.getBlockZ();
    }
    
    private void testRegeneration(Player player) {
        UUID playerUUID = player.getUniqueId();
        MineData mine = playerMines.get(playerUUID);
        
        if (mine == null) {
            player.sendMessage("§cVocê não possui uma mina ativa para testar.");
            return;
        }
        
        player.sendMessage("§6Teste de regeneração forçada!");
        
        int currentBroken = brokenBlocksCount.getOrDefault(playerUUID, 0);
        int totalBlocks = mine.getMineBlocks().size() + currentBroken;
        double threshold = config.getDouble("mine.auto_regeneration.threshold_percentage", 30.0);
        
        int targetBroken = (int) Math.ceil((threshold / 100.0) * totalBlocks);
        int blocksToAdd = Math.max(0, targetBroken - currentBroken);
        
        if (blocksToAdd > 0) {
            brokenBlocksCount.put(playerUUID, currentBroken + blocksToAdd);
            player.sendMessage("§eAdicionados " + blocksToAdd + " blocos quebrados para teste");
            player.sendMessage("§eTotal: " + (currentBroken + blocksToAdd) + "/" + totalBlocks + " (" + String.format("%.1f", threshold) + "%)");
            
            if (shouldTriggerRegeneration(mine)) {
                player.sendMessage("§aRegeneração deve ser ativada agora!");
                scheduleAutoRegeneration(mine, player);
            } else {
                player.sendMessage("§cRegeneração não foi ativada. Verifique logs.");
            }
        } else {
            player.sendMessage("§eJá atingiu o threshold! Threshold: " + threshold + "%");
            if (shouldTriggerRegeneration(mine)) {
                player.sendMessage("§aRegeneração deve ser ativada!");
                scheduleAutoRegeneration(mine, player);
            }
        }
    }
    
    private void forceRegeneration(Player player) {
        UUID playerUUID = player.getUniqueId();
        MineData mine = playerMines.get(playerUUID);
        
        if (mine == null) {
            player.sendMessage("§cVocê não possui uma mina ativa para forçar regeneração.");
            return;
        }
        
        player.sendMessage("§6Forçando regeneração manual!");
        
        scheduleAutoRegeneration(mine, player);
    }
    
    private void clearAllMines(Player player) {
        int totalMines = playerMines.size();
        
        if (totalMines == 0) {
            player.sendMessage("§eNão há minas ativas para limpar.");
            return;
        }
        
        player.sendMessage("§eIniciando limpeza de " + totalMines + " minas...");
        
        List<MineData> minesToRemove = new ArrayList<>(playerMines.values());
        
        final int[] removedCount = {0};
        
        new BukkitRunnable() {
            int currentIndex = 0;
            
            @Override
            public void run() {
                int processedThisTick = 0;
                int maxPerTick = 3;
                
                while (currentIndex < minesToRemove.size() && processedThisTick < maxPerTick) {
                    MineData mine = minesToRemove.get(currentIndex);
                    
                    try {
                        regenerateMine(mine);
                        removedCount[0]++;
                        
                        if (removedCount[0] % 10 == 0 || removedCount[0] == totalMines) {
                            player.sendMessage("§aProgresso: " + removedCount[0] + "/" + totalMines + " minas removidas");
                        }
                        
                    } catch (Exception e) {
                        
                    }
                    
                    currentIndex++;
                    processedThisTick++;
                }
                
                if (currentIndex < minesToRemove.size()) {
                    return;
                }
                
                player.sendMessage("§aLimpeza concluída! " + removedCount[0] + " minas foram removidas.");
                
                this.cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }
    
    private void cleanupDuplicateMinesCommand(Player player) {
        if (dataManager == null) {
            player.sendMessage("§cSistema de persistência não disponível!");
            return;
        }
        
        player.sendMessage("§eVerificando minas duplicadas no banco de dados...");
        
        dataManager.loadMines().thenAccept(allMines -> {
            if (allMines == null || allMines.isEmpty()) {
                player.sendMessage("§eNenhuma mina encontrada no banco de dados.");
                return;
            }
            
            Map<UUID, MineData> latestMines = new HashMap<>();
            List<MineData> duplicates = new ArrayList<>();
            
            for (MineData mine : allMines) {
                if (mine != null) {
                    UUID ownerUUID = mine.getOwnerUUID();
                    
                    if (latestMines.containsKey(ownerUUID)) {
                        MineData existingMine = latestMines.get(ownerUUID);
                        
                        if (mine.getExpirationTime() <= existingMine.getExpirationTime()) {
                            duplicates.add(mine);
                        } else {
                            duplicates.add(existingMine);
                            latestMines.put(ownerUUID, mine);
                        }
                    } else {
                        latestMines.put(ownerUUID, mine);
                    }
                }
            }
            
            if (duplicates.isEmpty()) {
                player.sendMessage("§aNenhuma mina duplicada encontrada!");
                return;
            }
            
            player.sendMessage("§eEncontradas " + duplicates.size() + " minas duplicadas. Iniciando limpeza...");
            
            final int[] removedCount = {0};
            for (MineData duplicate : duplicates) {
                dataManager.removeMine(duplicate).thenAccept(success -> {
                    if (success) {
                        removedCount[0]++;
                        if (removedCount[0] % 5 == 0 || removedCount[0] == duplicates.size()) {
                            player.sendMessage("§aProgresso: " + removedCount[0] + "/" + duplicates.size() + " duplicatas removidas");
                        }
                    }
                });
            }
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.sendMessage("§aLimpeza de duplicatas concluída! " + removedCount[0] + " minas duplicadas removidas.");
            }, 20L);
        });
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
            
            for (Block block : mine.getMineBlocks()) {
                mineBlocks.add(new BlockSerializable(block));
            }
        }
        
        public MineData toMineData() {
            Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
            List<BlockData> originalBlocksList = new ArrayList<>();
            List<Block> mineBlocksList = new ArrayList<>();
            
            for (BlockDataSerializable bds : originalBlocks) {
                originalBlocksList.add(bds.toBlockData());
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
            Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
            return new BlockData(location, Bukkit.createBlockData(material));
        }
    }
    
    private static class BlockSerializable {
        private final String worldName;
        private final double x, y, z;
        
        public BlockSerializable(Block block) {
            this.worldName = block.getWorld().getName();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
        }
        
        public Block toBlock() {
            return Bukkit.getWorld(worldName).getBlockAt((int)x, (int)y, (int)z);
        }
        
        public BlockData toBlockData() {
            Location location = new Location(Bukkit.getWorld(worldName), x, y, z);
            return new BlockData(location, Bukkit.createBlockData(Material.AIR));
        }
    }


}
