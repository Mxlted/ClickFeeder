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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClickFeeder implements ClientModInitializer {
    public static final String MOD_ID = "clickfeeder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final double FEED_RADIUS = 5.0;
    private static final double FEED_RADIUS_SQR = FEED_RADIUS * FEED_RADIUS;
    private static final int MAX_FEEDS_PER_CLICK = 20;
    private static final int HOTBAR_SIZE = Inventory.SELECTION_SIZE;
    private static final int INVENTORY_SIZE = Inventory.INVENTORY_SIZE;
    private static final long ADULT_FEED_RETRY_DELAY_TICKS = 600L;
    private static final Map<UUID, Long> RECENT_ADULT_FEEDS = new HashMap<>();

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
            if (mc.player != localPlayer || gameMode == null || localPlayer.connection == null || gameMode.isSpectator()) {
                return InteractionResult.PASS;
            }

            Item foodItem = held.getItem();
            long gameTime = world.getGameTime();
            pruneRecentAdultFeeds(gameTime);

            List<Animal> animals = findFeedableAnimals(world, localPlayer, held);
            if (animals.isEmpty()) {
                return InteractionResult.PASS;
            }

            int[] foodCounts = snapshotFoodCounts(localPlayer.getInventory(), foodItem);
            int availableFood = countAvailableFood(localPlayer, foodCounts);
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

                if (!ensureFoodInMainHand(localPlayer, gameMode, foodItem, foodCounts)) {
                    break;
                }

                ItemStack handStack = localPlayer.getMainHandItem();
                if (!canFeedAnimal(animal, localPlayer, handStack)) {
                    continue;
                }

                boolean adultFeedAttempt = !animal.canAgeUp();
                InteractionResult result = gameMode.interact(
                    localPlayer,
                    animal,
                    new EntityHitResult(animal),
                    InteractionHand.MAIN_HAND
                );
                if (result.consumesAction()) {
                    feedCount++;
                    noteFoodConsumed(localPlayer, foodCounts);
                    if (adultFeedAttempt) {
                        rememberAdultFeed(animal, gameTime);
                    }
                }
            }

            return InteractionResult.FAIL;
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
            && canUseFoodOn(animal, player.level().getGameTime());
    }

    private static boolean canUseFoodOn(Animal animal, long gameTime) {
        if (animal.isBaby()) {
            return false;
        }

        // Adult love/cooldown state is not fully synced to the client, so remember
        // adult animals this client just fed and avoid immediately retrying them.
        return animal.canFallInLove()
            && !hasRecentAdultFeed(animal, gameTime);
    }

    private static int[] snapshotFoodCounts(Inventory inv, Item foodItem) {
        int[] foodCounts = new int[INVENTORY_SIZE];
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (isFoodStack(stack, foodItem)) {
                foodCounts[i] = stack.getCount();
            }
        }
        return foodCounts;
    }

    private static int countAvailableFood(LocalPlayer player, int[] foodCounts) {
        if (player.hasInfiniteMaterials()) {
            return MAX_FEEDS_PER_CLICK;
        }

        int count = 0;
        for (int foodCount : foodCounts) {
            count += foodCount;
        }
        return count;
    }

    private static boolean ensureFoodInMainHand(
        LocalPlayer player,
        MultiPlayerGameMode gameMode,
        Item foodItem,
        int[] foodCounts
    ) {
        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();
        if (hasCountedFood(inv, selected, foodItem, foodCounts) && isFoodStack(player.getMainHandItem(), foodItem)) {
            return true;
        }

        int hotbarSlot = findFoodInHotbar(inv, foodItem, foodCounts);
        if (hotbarSlot >= 0) {
            return switchToSlot(player, hotbarSlot) && isFoodStack(player.getMainHandItem(), foodItem);
        }

        int inventorySlot = findFoodInInventory(inv, foodItem, foodCounts);
        if (inventorySlot < 0) {
            return false;
        }

        int targetHotbarSlot = findBestHotbarSlot(inv, foodCounts);
        if (!swapInventoryToHotbar(player, gameMode, inventorySlot, targetHotbarSlot)) {
            return false;
        }
        swapFoodCounts(foodCounts, inventorySlot, targetHotbarSlot);

        if (!isFoodStack(inv.getItem(targetHotbarSlot), foodItem)) {
            return false;
        }

        return switchToSlot(player, targetHotbarSlot) && isFoodStack(player.getMainHandItem(), foodItem);
    }

    private static int findFoodInHotbar(Inventory inv, Item foodItem, int[] foodCounts) {
        int selected = inv.getSelectedSlot();

        if (hasCountedFood(inv, selected, foodItem, foodCounts)) {
            return selected;
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && hasCountedFood(inv, i, foodItem, foodCounts)) {
                return i;
            }
        }

        return -1;
    }

    private static int findFoodInInventory(Inventory inv, Item foodItem, int[] foodCounts) {
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) {
            if (hasCountedFood(inv, i, foodItem, foodCounts)) {
                return i;
            }
        }
        return -1;
    }

    private static int findBestHotbarSlot(Inventory inv, int[] foodCounts) {
        int selected = inv.getSelectedSlot();

        if (isHotbarSlot(selected) && foodCounts[selected] <= 0) {
            return selected;
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (foodCounts[i] <= 0 && inv.getItem(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (i != selected && foodCounts[i] <= 0) {
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

        Inventory inv = player.getInventory();
        ItemStack sourceStack = inv.getItem(inventorySlot);
        if (sourceStack.isEmpty() || player.containerMenu != player.inventoryMenu) {
            return false;
        }

        int containerId = player.inventoryMenu.containerId;
        int containerSlot = inventorySlot;
        gameMode.handleContainerInput(containerId, containerSlot, hotbarSlot, ContainerInput.SWAP, player);
        return inv.getItem(hotbarSlot).getItem() == sourceStack.getItem();
    }

    private static boolean isFoodStack(ItemStack stack, Item foodItem) {
        return !stack.isEmpty() && stack.getItem() == foodItem;
    }

    private static boolean isHotbarSlot(int slot) {
        return Inventory.isHotbarSlot(slot);
    }

    private static boolean hasCountedFood(Inventory inv, int slot, Item foodItem, int[] foodCounts) {
        return slot >= 0
            && slot < INVENTORY_SIZE
            && foodCounts[slot] > 0
            && isFoodStack(inv.getItem(slot), foodItem);
    }

    private static void noteFoodConsumed(LocalPlayer player, int[] foodCounts) {
        if (player.hasInfiniteMaterials()) {
            return;
        }

        int selected = player.getInventory().getSelectedSlot();
        if (isHotbarSlot(selected) && foodCounts[selected] > 0) {
            foodCounts[selected]--;
        }
    }

    private static void swapFoodCounts(int[] foodCounts, int firstSlot, int secondSlot) {
        int firstCount = foodCounts[firstSlot];
        foodCounts[firstSlot] = foodCounts[secondSlot];
        foodCounts[secondSlot] = firstCount;
    }

    private static boolean hasRecentAdultFeed(Animal animal, long gameTime) {
        Long retryAfter = RECENT_ADULT_FEEDS.get(animal.getUUID());
        return retryAfter != null && retryAfter > gameTime;
    }

    private static void rememberAdultFeed(Animal animal, long gameTime) {
        RECENT_ADULT_FEEDS.put(animal.getUUID(), gameTime + ADULT_FEED_RETRY_DELAY_TICKS);
    }

    private static void pruneRecentAdultFeeds(long gameTime) {
        Iterator<Map.Entry<UUID, Long>> iterator = RECENT_ADULT_FEEDS.entrySet().iterator();
        while (iterator.hasNext()) {
            long retryAfter = iterator.next().getValue();
            if (retryAfter <= gameTime || retryAfter - gameTime > ADULT_FEED_RETRY_DELAY_TICKS) {
                iterator.remove();
            }
        }
    }
}
