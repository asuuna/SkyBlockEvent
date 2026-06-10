/*
 * SkyBlockEvent
 * Copyright (c) 2026 Shirito. All rights reserved.
 */
package com.skyblockevent.listener;

import com.skyblockevent.event.EventManager;
import com.skyblockevent.model.ActiveEvent;
import com.skyblockevent.model.CustomDropSettings;
import com.skyblockevent.model.EventDefinition;
import com.skyblockevent.model.EventType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class SkyBlockEventListener implements Listener {

    private final EventManager eventManager;
    private final NamespacedKey customDropEventKey;
    private final NamespacedKey customDropScoreKey;

    public SkyBlockEventListener(Plugin plugin, EventManager eventManager) {
        this.eventManager = eventManager;
        this.customDropEventKey = new NamespacedKey(plugin, "custom_drop_event");
        this.customDropScoreKey = new NamespacedKey(plugin, "custom_drop_score");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isBypassedGameMode(player)) {
            return;
        }

        Optional<ActiveEvent> optionalActiveEvent = eventManager.getActiveEvent();
        if (optionalActiveEvent.isEmpty()) {
            return;
        }

        ActiveEvent activeEvent = optionalActiveEvent.get();
        EventDefinition definition = activeEvent.getDefinition();
        Block block = event.getBlock();
        Material material = block.getType();
        boolean eligible = false;

        boolean farmBlock = isHarvestableFarmBlock(block);
        if (definition.getType() == EventType.MINING || definition.getType() == EventType.COMET_SHOWER) {
            eligible = definition.matchesMaterial(material) && !farmBlock;
        } else if (definition.getType() == EventType.FARMING || definition.getType() == EventType.RIFT_HARVEST) {
            eligible = definition.matchesMaterial(material) && farmBlock;
        } else if (definition.getType() == EventType.DOUBLE_DROPS) {
            eligible = definition.matchesMaterial(material);
        }

        if (!eligible) {
            return;
        }

        if (definition.getPointsPerAction() > 0) {
            eventManager.addScore(player, definition, definition.getPointsPerAction());
        }
        if (event.isDropItems()) {
            duplicateBlockDrops(event, definition);
        }
        spawnCustomDrop(definition, block.getLocation().add(0.5D, 0.5D, 0.5D));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || isBypassedGameMode(killer)) {
            return;
        }

        Optional<ActiveEvent> optionalActiveEvent = eventManager.getActiveEvent();
        if (optionalActiveEvent.isEmpty()) {
            return;
        }

        EventDefinition definition = optionalActiveEvent.get().getDefinition();
        if (definition.getType() != EventType.MOB_HUNT && definition.getType() != EventType.RELIC_HUNT) {
            return;
        }

        if (definition.getPointsPerAction() > 0) {
            eventManager.addScore(killer, definition, definition.getPointsPerAction());
        }
        duplicateItemStacks(event.getDrops(), definition.getDropMultiplier());
        spawnCustomDrop(definition, event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (isBypassedGameMode(player) || event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Optional<ActiveEvent> optionalActiveEvent = eventManager.getActiveEvent();
        if (optionalActiveEvent.isEmpty()) {
            return;
        }

        EventDefinition definition = optionalActiveEvent.get().getDefinition();
        if (definition.getType() != EventType.FISHING) {
            return;
        }

        if (definition.getPointsPerAction() > 0) {
            eventManager.addScore(player, definition, definition.getPointsPerAction());
        }
        if (event.getCaught() instanceof Item) {
            Item caught = (Item) event.getCaught();
            for (ItemStack extra : calculateExtraStacks(caught.getItemStack(), definition.getDropMultiplier())) {
                caught.getWorld().dropItemNaturally(caught.getLocation(), extra);
            }
            spawnCustomDrop(definition, caught.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player) entity;
        ItemStack stack = event.getItem().getItemStack();
        if (!stack.hasItemMeta()) {
            return;
        }

        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String eventId = data.get(customDropEventKey, PersistentDataType.STRING);
        Integer score = data.get(customDropScoreKey, PersistentDataType.INTEGER);
        if (eventId == null || score == null) {
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();
        eventManager.getActiveDefinitionById(eventId)
            .ifPresent(definition -> eventManager.addScore(player, definition, score));
    }

    private void spawnCustomDrop(EventDefinition definition, Location location) {
        CustomDropSettings settings = definition.getCustomDropSettings();
        if (!settings.isEnabled() || location.getWorld() == null) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > settings.getChance()) {
            return;
        }

        ItemStack stack = new ItemStack(settings.getMaterial(), 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(settings.getDisplayName()));
            if (!settings.getLore().isEmpty()) {
                meta.setLore(color(settings.getLore()));
            }
            meta.getPersistentDataContainer().set(customDropEventKey, PersistentDataType.STRING, definition.getId());
            meta.getPersistentDataContainer().set(customDropScoreKey, PersistentDataType.INTEGER, settings.getScore());
            stack.setItemMeta(meta);
        }

        Item item = location.getWorld().dropItemNaturally(location, stack);
        item.setGlowing(settings.isGlowing());
        item.setPickupDelay(10);
    }

    private void duplicateBlockDrops(BlockBreakEvent event, EventDefinition definition) {
        double multiplier = eventManager.getDropMultiplier(definition);
        if (multiplier <= 1.0D) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        Collection<ItemStack> drops = event.getBlock().getDrops(tool);
        for (ItemStack drop : drops) {
            for (ItemStack extra : calculateExtraStacks(drop, multiplier)) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), extra);
            }
        }
    }

    private void duplicateItemStacks(Collection<ItemStack> drops, double multiplier) {
        if (multiplier <= 1.0D || drops.isEmpty()) {
            return;
        }
        java.util.List<ItemStack> extras = new java.util.ArrayList<>();
        for (ItemStack drop : drops) {
            extras.addAll(calculateExtraStacks(drop, multiplier));
        }
        drops.addAll(extras);
    }

    private java.util.List<ItemStack> calculateExtraStacks(ItemStack original, double multiplier) {
        if (original == null || original.getType().isAir() || multiplier <= 1.0D) {
            return java.util.List.of();
        }

        int guaranteedCopies = Math.max(0, (int) Math.floor(multiplier) - 1);
        double fractionalChance = multiplier - Math.floor(multiplier);
        java.util.List<ItemStack> extras = new java.util.ArrayList<>();

        for (int i = 0; i < guaranteedCopies; i++) {
            extras.add(original.clone());
        }
        if (fractionalChance > 0.0D && ThreadLocalRandom.current().nextDouble() < fractionalChance) {
            extras.add(original.clone());
        }
        return extras;
    }

    private boolean isHarvestableFarmBlock(Block block) {
        if (block.getBlockData() instanceof Ageable) {
            Ageable ageable = (Ageable) block.getBlockData();
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        String name = block.getType().name();
        return name.equals("MELON")
            || name.equals("PUMPKIN")
            || name.equals("SUGAR_CANE")
            || name.equals("CACTUS")
            || name.equals("BAMBOO")
            || name.equals("KELP");
    }

    private boolean isBypassedGameMode(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private List<String> color(List<String> lines) {
        return lines.stream().map(this::color).collect(Collectors.toList());
    }
}
