/*
 * Copyright (c) 2023, Truth Forger <http://github.com/Blackberry0Pie>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bbp.essencepouchhealth;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "Runecrafting counter"
)
public class EssencePouchHealthPlugin extends Plugin
{
	private static final int MEDIUM_POUCH = ItemID.MEDIUM_POUCH;
	private static final int LARGE_POUCH = ItemID.LARGE_POUCH;
	private static final int GIANT_POUCH = ItemID.GIANT_POUCH;
	private static final int COLOSSAL_POUCH = ItemID.COLOSSAL_POUCH;

	private static final int MEDIUM_POUCH_USES = 44*6; //264
	private static final int LARGE_POUCH_USES = 31*9; //279
	private static final int GIANT_POUCH_USES = 10*12; //120
	private static final int COLOSSAL_POUCH_USES = 8*40; //320

	private static final List<Integer> healthyPouchList = List.of(MEDIUM_POUCH, LARGE_POUCH, GIANT_POUCH, COLOSSAL_POUCH);
	private static final List<Integer> degradedPouchList = List.of(ItemID.MEDIUM_POUCH_5511, ItemID.LARGE_POUCH_5513, ItemID.GIANT_POUCH_5515, ItemID.COLOSSAL_POUCH_26786 /* TODO check if this is not the quest pouch id */);
	private static final List<Integer> essenceList = List.of(ItemID.DAEYALT_ESSENCE, ItemID.PURE_ESSENCE, ItemID.GUARDIAN_ESSENCE);

	@Getter
	private Map<Integer, Integer> itemUses = new HashMap<>() {{
		put(MEDIUM_POUCH, 0);
		put(LARGE_POUCH, 0);
		put(GIANT_POUCH, 0);
		put(COLOSSAL_POUCH, 0);
	}};

	protected final Map<Integer, Integer> maxItemUses = new HashMap<>() {{
		put(MEDIUM_POUCH, MEDIUM_POUCH_USES);
		put(LARGE_POUCH, LARGE_POUCH_USES);
		put(GIANT_POUCH, GIANT_POUCH_USES);
		put(COLOSSAL_POUCH, COLOSSAL_POUCH_USES);
	}};

	private Multiset<Integer> previousInventorySnapshot;
	private int lastClickedItem = -1;

	@Inject
	private Client client;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private EssencePouchHealthOverlay rcOverlay;
	@Inject
	private ItemManager itemManager;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(rcOverlay);
		previousInventorySnapshot = getInventorySnapshot(); //TODO test if this works
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(rcOverlay);
	}

	private Multiset<Integer> getInventorySnapshot()
	{
		final ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		Multiset<Integer> inventorySnapshot = HashMultiset.create();

		if (inventory != null)
		{
			Arrays.stream(inventory.getItems())
					.forEach(item -> inventorySnapshot.add(item.getId(), item.getQuantity()));
		}

		return inventorySnapshot;
	}
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (previousInventorySnapshot == null) return;
		if (lastClickedItem == -1) return;
		Multiset<Integer> currentInventorySnapshot = getInventorySnapshot();
		final Multiset<Integer> itemsRemoved = Multisets.difference(previousInventorySnapshot, currentInventorySnapshot);
		if (itemsRemoved.isEmpty()){
			log.info("Did not actually fill anything...");
			return;
		}

		int removedEssenceCount = (int)itemsRemoved.stream().filter(essenceList::contains).count();
		if (removedEssenceCount > 0 ) {
			log.info("Stored {} items", removedEssenceCount);
			itemUses.put(lastClickedItem, itemUses.get(lastClickedItem) + removedEssenceCount);
		}

		Stream<Integer> degradedPouchesRemoved = itemsRemoved.stream().filter(degradedPouchList::contains);
		if (degradedPouchesRemoved.findAny().isPresent()) {
			final Multiset<Integer> itemsAdded = Multisets.difference(currentInventorySnapshot, previousInventorySnapshot);
			if ((int)itemsAdded.stream().filter(healthyPouchList::contains).count() > 0) {
				//Repair all pouches
				itemUses.put(MEDIUM_POUCH, MEDIUM_POUCH_USES);
				itemUses.put(LARGE_POUCH, LARGE_POUCH_USES);
				itemUses.put(GIANT_POUCH, GIANT_POUCH_USES);
				itemUses.put(COLOSSAL_POUCH, COLOSSAL_POUCH_USES);
			}
		}

		lastClickedItem = -1;
	}

	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event)
	{
		lastClickedItem = -1;
		if (event.getMenuOption() == null || !event.getMenuOption().equals("Fill")) {
			return;
		}
		int inventoryIndex = event.getParam0();
		final int itemId;
		final String itemName;

		ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INVENTORY);
		if (inventoryContainer == null)
			return;
		Item item = inventoryContainer.getItem(inventoryIndex);
		if (item == null)
			return;
		itemId = item.getId();
		itemName = item.toString();

		if (!itemUses.containsKey(itemId)) {
			log.info("Filled an item that we don't know about: {} with ID: {}", itemName, itemId);
			return;
		}
		previousInventorySnapshot = getInventorySnapshot();
		lastClickedItem = itemId;
	}
}
