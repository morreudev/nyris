package com.morreu.nyris;

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

import java.util.*;

public final class Nyris extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<UUID, MineData> playerMines;
    private Map<Location, MineData> locationMines;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        
        playerMines = new HashMap<>();
        locationMines = new HashMap<>();
        
        getServer().getPluginManager().registerEvents(this, this);
        
        getCommand("nyris").setExecutor(this);
        
        getLogger().info("Plugin Nyris ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        for (MineData mine : playerMines.values()) {
            regenerateMine(mine);
        }
        getLogger().info("Plugin Nyris desativado!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando só pode ser usado por jogadores!");
            return true;
        }

        Player player = (Player) sender;
        
        if (command.getName().equalsIgnoreCase("nyris")) {
            giveMineItem(player);
            player.sendMessage("§aVocê recebeu o item especial da mina!");
            return true;
        }
        
        return false;
    }

    private void giveMineItem(Player player) {
        ItemStack mineItem = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = mineItem.getItemMeta();
        meta.setDisplayName("§6Mina Personalizada");
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
        
        if (item.getItemMeta().getDisplayName().equals("§6Mina Personalizada")) {
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
        
        for (int i = 0; i < 100; i++) {
            Location loc = eyeLocation.clone().add(direction.clone().multiply(i));
            if (loc.getBlock().getType() != Material.AIR) {
                return loc;
            }
        }
        return null;
    }

    private void createMine(Player player, Location center) {
        if (playerMines.containsKey(player.getUniqueId())) {
            player.sendMessage("§cVocê já possui uma mina ativa! Use /nyris para obter outro item.");
            return;
        }

        int width = config.getInt("mine.dimensions.width", 10);
        int depth = config.getInt("mine.dimensions.depth", 10);
        int height = config.getInt("mine.dimensions.height", 5);
        int activeTime = config.getInt("mine.duration.active_time", 3600);
        int warningTime = config.getInt("mine.duration.warning_time", 300);
        
        Location mineLocation = center.clone();
        
        List<Block> mineBlocks = new ArrayList<>();
        List<BlockData> originalBlocks = new ArrayList<>();
        
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfDepth; z <= halfDepth; z++) {
                for (int y = 0; y <= height; y++) {
                    Location loc = mineLocation.clone().add(x, y, z);
                    Block block = loc.getBlock();
                    
                    if (block.getType() != Material.AIR) {
                        mineBlocks.add(block);
                        originalBlocks.add(new BlockData(loc, block.getBlockData()));
                    }
                }
            }
        }
        
        List<Material> oreTypes = getOreTypes();
        Random random = new Random();
        
        for (Block block : mineBlocks) {
            Material oreType = oreTypes.get(random.nextInt(oreTypes.size()));
            block.setType(oreType);
        }
        
        MineData mineData = new MineData(
            player.getUniqueId(),
            mineLocation,
            width,
            depth,
            height,
            originalBlocks,
            mineBlocks,
            System.currentTimeMillis() + (activeTime * 1000L)
        );
        
        playerMines.put(player.getUniqueId(), mineData);
        locationMines.put(mineLocation, mineData);
        
        playEffects(mineLocation, "creation");
        
        player.sendMessage("§aMina criada com sucesso!");
        
        scheduleMineExpiration(mineData, warningTime);
    }

    private void scheduleMineExpiration(MineData mine, int warningTime) {
        long warningTimeMs = mine.getExpirationTime() - (warningTime * 1000L);
        long currentTime = System.currentTimeMillis();
        
        if (warningTimeMs > currentTime) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (playerMines.containsKey(mine.getOwnerUUID())) {
                        Player player = Bukkit.getPlayer(mine.getOwnerUUID());
                        if (player != null) {
                            player.sendMessage("§e⚠ Aviso: Sua mina expira em " + (warningTime / 60) + " minutos!");
                            playEffects(mine.getLocation(), "warning");
                        }
                    }
                }
            }.runTaskLater(this, (warningTimeMs - currentTime) / 50);
        }
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (playerMines.containsKey(mine.getOwnerUUID())) {
                    Player player = Bukkit.getPlayer(mine.getOwnerUUID());
                    if (player != null) {
                        player.sendMessage("§c⏰ Sua mina expirou e foi removida!");
                    }
                    playEffects(mine.getLocation(), "expiration");
                    regenerateMine(mine);
                }
            }
        }.runTaskLater(this, (mine.getExpirationTime() - currentTime) / 50);
    }

    private void playEffects(Location location, String effectType) {
        String soundName = config.getString("effects." + effectType + ".sound", "BLOCK_NOTE_BLOCK_PLING");
        double volume = config.getDouble("effects." + effectType + ".volume", 1.0);
        double pitch = config.getDouble("effects." + effectType + ".pitch", 1.0);
        String particleName = config.getString("effects." + effectType + ".particles", "NOTE");
        int particleCount = config.getInt("effects." + effectType + ".particle_count", 20);
        
        try {
            Sound sound = Sound.valueOf(soundName);
            location.getWorld().playSound(location, sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Som inválido na configuração: " + soundName);
        }
        
        try {
            Particle particle = Particle.valueOf(particleName);
            location.getWorld().spawnParticle(particle, location, particleCount);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Partícula inválida na configuração: " + particleName);
        }
    }

    private List<Material> getOreTypes() {
        List<String> oreNames = config.getStringList("mine.ore_types");
        List<Material> oreTypes = new ArrayList<>();
        
        for (String oreName : oreNames) {
            try {
                Material material = Material.valueOf(oreName.toUpperCase());
                oreTypes.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Material inválido na configuração: " + oreName);
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
        
        return oreTypes;
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
            
            if (mine.isEmpty()) {
                player.sendMessage("§aMina esgotada! Regenerando em 30 segundos...");
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        regenerateMine(mine);
                        player.sendMessage("§aMina regenerada!");
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
        int halfWidth = mine.getWidth() / 2;
        int halfDepth = mine.getDepth() / 2;
        int height = mine.getHeight();
        
        return location.getWorld().equals(mineLoc.getWorld()) &&
               Math.abs(location.getBlockX() - mineLoc.getBlockX()) <= halfWidth &&
               Math.abs(location.getBlockZ() - mineLoc.getBlockZ()) <= halfDepth &&
               location.getBlockY() >= mineLoc.getBlockY() &&
               location.getBlockY() <= mineLoc.getBlockY() + height;
    }

    private void regenerateMine(MineData mine) {
        for (BlockData blockData : mine.getOriginalBlocks()) {
            blockData.getLocation().getBlock().setBlockData(blockData.getBlockData());
        }
        
        playerMines.remove(mine.getOwnerUUID());
        locationMines.remove(mine.getLocation());
    }

    private static class BlockData {
        private final Location location;
        private final org.bukkit.block.data.BlockData blockData;
        
        public BlockData(Location location, org.bukkit.block.data.BlockData blockData) {
            this.location = location;
            this.blockData = blockData;
        }
        
        public Location getLocation() { return location; }
        public org.bukkit.block.data.BlockData getBlockData() { return blockData; }
    }

    private static class MineData {
        private final UUID ownerUUID;
        private final Location location;
        private final int width;
        private final int depth;
        private final int height;
        private final List<BlockData> originalBlocks;
        private final List<Block> mineBlocks;
        private final long expirationTime;
        
        public MineData(UUID ownerUUID, Location location, int width, int depth, int height, 
                        List<BlockData> originalBlocks, List<Block> mineBlocks, long expirationTime) {
            this.ownerUUID = ownerUUID;
            this.location = location;
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.originalBlocks = originalBlocks;
            this.mineBlocks = mineBlocks;
            this.expirationTime = expirationTime;
        }
        
        public UUID getOwnerUUID() { return ownerUUID; }
        public Location getLocation() { return location; }
        public int getWidth() { return width; }
        public int getDepth() { return depth; }
        public int getHeight() { return height; }
        public List<BlockData> getOriginalBlocks() { return originalBlocks; }
        public List<Block> getMineBlocks() { return mineBlocks; }
        public long getExpirationTime() { return expirationTime; }
        
        public void removeBlock(Block block) {
            mineBlocks.remove(block);
        }
        
        public boolean isEmpty() {
            return mineBlocks.isEmpty();
        }
    }
}
