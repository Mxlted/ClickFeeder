package com.clickfeeder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class ClickFeeder implements ClientModInitializer {
    public static final String MOD_ID = "clickfeeder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double FEED_RADIUS = 5.0;
    private static final double FEED_RADIUS_SQR = FEED_RADIUS * FEED_RADIUS;
    private static final int MAX_FEEDS_PER_CLICK = 20;
    private static final int HOTBAR_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;

    @Override
    public void onInitializeClient() {
        LOGGER.info("ClickFeeder initializing...");

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND || !world.isClientSide()) {
                return InteractionResult.PASS;
            }

            if (!(player instanceof LocalPlayer localPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack held = localPlayer.getMainHandItem();
            if (held.isEmpty()) {
                return InteractionResult.PASS;
            }

            Minecraft mc = Minecraft.getInstance();
            MultiPlayerGameMode gameMode = mc.gameMode;
            if (mc.player != localPlayer || gameMode == null || localPlayer.connection == null) {
                return InteractionResult.PASS;
            }

            Item foodItem = held.getItem();
            List<Animal> animals = findFeedableAnimals(world, localPlayer, held);
            if (animals.isEmpty()) {
                return InteractionResult.PASS;
            }

            int availableFood = countAvailableFood(localPlayer.getInventory(), foodItem);
            int animalsToFeed = Math.min(animals.size(), Math.min(availableFood, MAX_FEEDS_PER_CLICK));

            if (animalsToFeed == 0) {
                return InteractionResult.PASS;
            }

            int feedCount = 0;

            for (Animal animal : animals) {
                if (feedCount >= animalsToFeed) {
                    break;
                }

                if (!isTargetableAnimal(animal, localPlayer)) {
                    continue;
                }

                if (!ensureFoodInMainHand(localPlayer, gameMode, foodItem)) {
                    break;
                }

                ItemStack handStack = localPlayer.getMainHandItem();
                if (!canFeedAnimal(animal, localPlayer, handStack)) {
                    continue;
                }

                InteractionResult result = gameMode.interact(
                    localPlayer,
                    animal,
                    new EntityHitResult(animal),
                    InteractionHand.MAIN_HAND
                );
                if (result.consumesAction()) {
                    feedCount++;
                }
            }

            return feedCount > 0 ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    private static List<Animal> findFeedableAnimals(Level world, LocalPlayer player, ItemStack food) {
        AABB box = player.getBoundingBox().inflate(FEED_RADIUS);
        List<Animal> animals = world.getEntitiesOfClass(
            Animal.class,
            box,
            animal -> canFeedAnimal(animal, player, food)
        );
        animals.sort(Comparator.comparingDouble(animal -> animal.distanceToSqr(player)));
        return animals;
    }

    private static boolean canFeedAnimal(Animal animal, LocalPlayer player, ItemStack food) {
        return isTargetableAnimal(animal, player) && animal.isFood(food);
    }

    private static boolean isTargetableAnimal(Animal animal, LocalPlayer player) {
        return animal.isAlive()
            && !animal.isRemoved()
            && animal.distanceToSqr(player) <= FEED_RADIUS_SQR
            && (animal.isBaby() || !animal.isInLove());
    }

    private static int countAvailableFood(Inventory inv, Item foodItem) {
        int count = 0;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (isFoodStack(stack, foodItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean ensureFoodInMainHand(LocalPlayer player, MultiPlayerGameMode gameMode, Item foodItem) {
        if (isFoodStack(player.getMainHandItem(), foodItem)) {
            return true;
        }

        Inventory inv = player.getInventory();
        int hotbarSlot = findFoodInHotbar(inv, foodItem);
        if (hotbarSlot >= 0) {
            return switchToSlot(player, hotbarSlot) && isFoodStack(player.getMainHandItem(), foodItem);
        }

        int inventorySlot = findFoodInInventory(inv, foodItem);
        if (inventorySlot < 0) {
            return false;
        }

        int targetHotbarSlot = findBestHotbarSlot(inv, foodItem);
        if (!swapInventoryToHotbar(player, gameMode, inventorySlot, targetHotbarSlot)) {
            return false;
        }

        return switchToSlot(player, targetHotbarSlot) && isFoodStack(player.getMainHandItem(), foodItem);
    }

    private static int findFoodInHotbar(Inventory inv, Item foodItem) {
        int selected = inv.getSelectedSlot();

        if (isHotbarSlot(selected) && isFoodStack(inv.getItem(selected), foodItem)) {
            return selected;
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && isFoodStack(inv.getItem(i), foodItem)) {
                return i;
            }
        }

        return -1;
    }

    private static int findFoodInInventory(Inventory inv, Item foodItem) {
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            if (isFoodStack(inv.getItem(i), foodItem)) {
                return i;
            }
        }
        return -1;
    }

    private static int findBestHotbarSlot(Inventory inv, Item foodItem) {
        int selected = inv.getSelectedSlot();

        if (isHotbarSlot(selected) && !isFoodStack(inv.getItem(selected), foodItem)) {
            return selected;
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (inv.getItem(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && !isFoodStack(inv.getItem(i), foodItem)) {
                return i;
            }
        }

        return isHotbarSlot(selected) ? selected : 0;
    }

    private static boolean switchToSlot(LocalPlayer player, int slot) {
        if (!isHotbarSlot(slot) || player.connection == null) {
            return false;
        }

        if (player.getInventory().getSelectedSlot() == slot) {
            return true;
        }

        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        return player.getInventory().getSelectedSlot() == slot;
    }

    private static boolean swapInventoryToHotbar(
        LocalPlayer player,
        MultiPlayerGameMode gameMode,
        int inventorySlot,
        int hotbarSlot
    ) {
        if (inventorySlot < HOTBAR_SIZE || inventorySlot >= INVENTORY_SIZE || !isHotbarSlot(hotbarSlot)) {
            return false;
        }

        int containerId = player.inventoryMenu.containerId;
        int containerSlot = inventorySlot;
        gameMode.handleContainerInput(containerId, containerSlot, hotbarSlot, ContainerInput.SWAP, player);
        return true;
    }

    private static boolean isFoodStack(ItemStack stack, Item foodItem) {
        return !stack.isEmpty() && stack.getItem() == foodItem;
    }

    private static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE;
    }
}
