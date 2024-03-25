package com.afelia.FakeWaterPlugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FakeWaterPlugin extends JavaPlugin implements Listener {

    private final String FAKE_WATER_METADATA_KEY = "FakeWater";
    private final NamespacedKey FAKE_WATER_TAG = new NamespacedKey(this, "fake_water");
    private Map<Player, Long> lastDamageTimes = new HashMap<>();
    private long damageIntervalTicks = 20L; // Adjust as needed
    private List<Block> fakeWaterBlocks;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("getfakewaterbucket").setExecutor(new GiveFakeWaterBucketCommand(this)); // Register command executor
        getLogger().info("FakeWaterPlugin has been enabled.");

        fakeWaterBlocks = new ArrayList<>();
        loadFakeWaterBlocks(); // Load fake water blocks from data.yml

        // Start the fake water damage loop for all players
        getServer().getOnlinePlayers().forEach(this::startFakeWaterDamage);
        startDamageLoop();
    }

    private void startDamageLoop() {
        Bukkit.getScheduler().runTaskTimer(this, this::applyFakeWaterDamage, 0L, 20L);
    }

    private void applyFakeWaterDamage() {
        // Loop through all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check if the player is in fake water
            if (isPlayerInFakeWater(player)) {
                // Get the time when the player last took damage
                long lastDamageTime = lastDamageTimes.getOrDefault(player, 0L);
                long currentTime = System.currentTimeMillis();
                // Check if the damage interval has passed since the last damage
                if (currentTime - lastDamageTime >= damageIntervalTicks) {
                    // Deal damage to the player
                    player.damage(1);
                    // Update the last damage time for the player
                    lastDamageTimes.put(player, currentTime);
                }
            }
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Block fromBlock = event.getBlock();
        Block toBlock = event.getToBlock();

        // Check if the block from is water and has the PDC
        if (fromBlock.getType() == Material.WATER && hasFakeWaterPDC(fromBlock)) {
            // Apply the PDC to the toBlock
            applyFakeWaterPDC(toBlock);
        }
    }

    private boolean hasFakeWaterPDC(Block block) {
        return block.hasMetadata(FAKE_WATER_METADATA_KEY);
    }

    private void gatherLake(Location location, int maxRadius) {
        World world = location.getWorld();
        Queue<Block> queue = new LinkedList<>();
        Set<Block> visited = new HashSet<>();

        Block startBlock = location.getBlock();
        queue.add(startBlock);
        visited.add(startBlock);

        while (!queue.isEmpty()) {
            Block currentBlock = queue.poll();

            // Apply fake water PDC to the current block
            applyFakeWaterPDC(currentBlock);

            // Check neighboring blocks within maxRadius
            for (int x = -maxRadius; x <= maxRadius; x++) {
                for (int y = -maxRadius; y <= maxRadius; y++) {
                    for (int z = -maxRadius; z <= maxRadius; z++) {
                        Block neighbor = world.getBlockAt(currentBlock.getX() + x, currentBlock.getY() + y, currentBlock.getZ() + z);
                        if (!visited.contains(neighbor) && neighbor.getType() == Material.WATER) {
                            queue.add(neighbor);
                            visited.add(neighbor);
                        }
                    }
                }
            }
        }
    }

    private void applyFakeWaterPDC(Block block) {
        // Apply the PDC to the block
        block.setMetadata(FAKE_WATER_METADATA_KEY, new FixedMetadataValue(this, true));
    }

    @Override
    public void onDisable() {
        getLogger().info("FakeWaterPlugin has been disabled.");
        saveFakeWaterBlocks(); // Save fake water blocks to data.yml
    }


    private void loadFakeWaterBlocks() {
        File file = new File(getDataFolder(), "data.yml");
        if (!file.exists()) {
            getLogger().info("Data file not found. Creating a new one...");
            saveResource("data.yml", false);
        }

        fakeWaterBlocks.clear(); // Clear the list to avoid duplicates
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(file);
        List<String> blockStrings = dataConfig.getStringList("fakeWaterBlocks");
        for (String blockString : blockStrings) {
            String[] parts = blockString.split(":");
            if (parts.length == 4) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                String worldName = parts[3];
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Block block = world.getBlockAt(x, y, z);
                    fakeWaterBlocks.add(block);
                } else {
                    getLogger().warning("Failed to load fake water block: World '" + worldName + "' not found.");
                }
            }
        }
        getLogger().info("Loaded " + fakeWaterBlocks.size() + " fake water blocks from data.yml.");
    }


    private void saveFakeWaterBlocks() {
        File file = new File(getDataFolder(), "data.yml");
        if (!file.exists()) {
            getLogger().info("Data file not found. Creating a new one...");
            saveResource("data.yml", false);
        }

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(file);
        List<String> blockStrings = new ArrayList<>();
        for (Block block : fakeWaterBlocks) {
            blockStrings.add(block.getX() + ":" + block.getY() + ":" + block.getZ() + ":" + block.getWorld().getName());
        }
        dataConfig.set("fakeWaterBlocks", blockStrings);

        try {
            // Save the data using the existing FileConfiguration object
            dataConfig.save(file);
            getLogger().info("Saved " + fakeWaterBlocks.size() + " fake water blocks to data.yml.");
        } catch (IOException e) {
            getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }
    private FileConfiguration getDataConfig() {
        File file = new File(getDataFolder(), "data.yml");
        return YamlConfiguration.loadConfiguration(file);
    }
    private void saveDataConfig(FileConfiguration dataConfig) {
        File file = new File(getDataFolder(), "data.yml");
        try {
            dataConfig.save(file);
        } catch (IOException e) {
            getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }
    private void setBlock(Block block) {
        Location location = block.getLocation();
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String key = worldName + "_" + x + "_" + y + "_" + z;

        FileConfiguration dataConfig = getDataConfig();
        dataConfig.set("fakeWaterBlocks." + key, true);

        saveDataConfig(dataConfig);
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();
        Block feetBlock = playerLocation.getBlock().getRelative(BlockFace.DOWN);
        Block headBlock = playerLocation.add(0, 1, 0).getBlock(); // Block at head level

        if ((feetBlock.getType() == Material.WATER && feetBlock.hasMetadata(FAKE_WATER_METADATA_KEY)) ||
                (headBlock.getType() == Material.WATER && headBlock.hasMetadata(FAKE_WATER_METADATA_KEY))) {
            startFakeWaterDamage(player); // Start continuous damage
            player.sendMessage("You are poisoned by FakeWater!");
        } else {
            stopFakeWaterDamage(player); // Stop continuous damage
        }
    }


    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        if (event.getNewState().getType() == Material.WATER && event.getBlock().getBlockData() instanceof org.bukkit.block.data.Levelled) {
            event.setCancelled(true); // Cancel the water formation
            setFakeWaterBlock(event.getBlock()); // Set our fake water block
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == DamageCause.POISON) {
            event.setCancelled(true); // Cancel poison damage
        }
    }

    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getBlockClicked().getRelative(event.getBlockFace());
        if (player.getInventory().getItemInMainHand().hasItemMeta() && player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(FAKE_WATER_TAG, PersistentDataType.STRING)) {
            markAsFakeWater(clickedBlock); // Mark the water source as Fake Water
        }
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getBlockClicked().getRelative(event.getBlockFace());
        if (player.getInventory().getItemInMainHand().hasItemMeta() && player.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(FAKE_WATER_TAG, PersistentDataType.STRING)) {
            markAsFakeWater(clickedBlock); // Mark the water source as Fake Water
        }
    }

    private void markAsFakeWater(Block block) {
        block.setType(Material.AIR); // Set block type to air to remove any existing block
        block.setMetadata(FAKE_WATER_METADATA_KEY, new FixedMetadataValue(this, true));
        fakeWaterBlocks.add(block); // Add the block to the fake water blocks list
    }


    private void setFakeWaterBlock(Block block) {
        block.setType(Material.WATER); // Set block to water
        block.setMetadata(FAKE_WATER_METADATA_KEY, new FixedMetadataValue(this, true));
        fakeWaterBlocks.add(block); // Add the block to the fake water blocks list
    }

    private boolean isPlayerInFakeWater(Player player) {
        Location loc = player.getLocation();
        Block feetBlock = loc.getBlock().getRelative(BlockFace.DOWN);
        Block headBlock = loc.getBlock();

        return (feetBlock.getType() == Material.WATER && feetBlock.hasMetadata(FAKE_WATER_METADATA_KEY)) ||
                (headBlock.getType() == Material.WATER && headBlock.hasMetadata(FAKE_WATER_METADATA_KEY));
    }


    private void startFakeWaterDamage(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isPlayerInFakeWater(player)) {
                    boolean killPlayerOnEnd = getConfig().getBoolean("Kill-player-on-end", true);
                    if (killPlayerOnEnd) {
                        if (player.getHealth() <= 0) {
                            player.setHealth(0);
                        } else {
                            player.damage(1); // Apply damage every tick (adjust as needed)
                        }
                    } else {
                        if (player.getHealth() > 0.5) {
                            player.damage(1); // Apply damage every tick (adjust as needed)
                        } else {
                            cancel(); // Stop applying damage if player reaches half a heart
                        }
                    }
                } else {
                    cancel(); // Stop applying damage if player leaves fake water
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 20 ticks = 1 second
    }

    private void stopFakeWaterDamage(Player player) {
        // Remove the metadata indicating fake water damage
        player.removeMetadata("fakeWaterDamage", this);
    }

    private static class GiveFakeWaterBucketCommand implements CommandExecutor {
        private final FakeWaterPlugin plugin;

        public GiveFakeWaterBucketCommand(FakeWaterPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (label.equalsIgnoreCase("getfakewaterbucket")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    plugin.giveFakeWaterBucket(player);
                    return true;
                } else {
                    sender.sendMessage("Only players can execute this command.");
                    return false;
                }
            }
            return false;
        }
    }

    private void giveFakeWaterBucket(Player player) {
        ItemStack fakeWaterBucket = new ItemStack(Material.WATER_BUCKET);
        ItemMeta meta = fakeWaterBucket.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f§lFake Water"); // Set display name with color codes
            meta.getPersistentDataContainer().set(FAKE_WATER_TAG, PersistentDataType.STRING, "true"); // Add custom NBT tag
            fakeWaterBucket.setItemMeta(meta);
        }
        player.getInventory().addItem(fakeWaterBucket);
        player.getInventory().addItem(fakeWaterBucket);
        player.sendMessage("You've received a Fake Water Bucket!");
    }
}

