package dev.quantumspigot.server.inventory;

import java.util.Arrays;
import java.util.List;
import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangeSubscriber;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Owner-thread, fixed-size inventory slots. Shared by hopper classification and block-entity wake-up hooks. */
public final class TrackedItemList extends NonNullList<ItemStack> implements ChangeSubscriber.CountChangeSubscriber<ItemStack> {
    private long revision;
    private long classifiedRevision = -1;
    private int fullState;
    private boolean recheckSubscriptions;
    private final Runnable onChange;
    private boolean attached = true;

    public TrackedItemList(List<ItemStack> contents) {
        this(contents, () -> {});
    }

    public TrackedItemList(List<ItemStack> contents, Runnable onChange) {
        super(Arrays.asList(contents.toArray(ItemStack[]::new)), ItemStack.EMPTY);
        this.onChange = onChange;
        for (int i = 0; i < this.size(); i++) this.subscribe(i, this.get(i));
    }

    public long revision() { return this.revision; }

    private void subscribe(int slot, ItemStack stack) {
        if (!this.attached) return;
        if (!stack.isEmpty()) {
            if (!stack.lithium$isSubscribedWithData(this, slot)) {
                stack.lithium$subscribe(this, slot);
                this.changed();
            }
        } else if (stack != ItemStack.EMPTY) this.recheckSubscriptions = true;
    }

    @Override
    public ItemStack set(int slot, ItemStack stack) {
        ItemStack old = super.set(slot, stack);
        if (old != stack && !old.isEmpty()) old.lithium$unsubscribeWithData(this, slot);
        this.subscribe(slot, stack);
        this.changed();
        return old;
    }

    /** Called when NBT/component loading replaces the inventory list. */
    public void detach() {
        this.attached = false;
        for (int i = 0; i < this.size(); i++) {
            ItemStack stack = this.get(i);
            if (!stack.isEmpty()) stack.lithium$unsubscribeWithData(this, i);
        }
    }

    public void attach() {
        this.attached = true;
        this.recheckSubscriptions = false;
        for (int i = 0; i < this.size(); i++) this.subscribe(i, this.get(i));
        this.changed();
    }

    /** An empty non-canonical stack can be resurrected without notifying anybody; keep its machine awake. */
    public boolean canSleep() {
        if (!this.attached) return false;
        if (this.recheckSubscriptions) {
            this.recheckSubscriptions = false;
            for (int i = 0; i < this.size(); i++) this.subscribe(i, this.get(i));
        }
        return !this.recheckSubscriptions;
    }

    private void changed() {
        this.revision++;
        if (this.attached) this.onChange.run();
    }

    /** Matches Paper's 0 empty / 1 has items / 2 full classification, including item stack-size components. */
    public int fullState() {
        this.canSleep();
        if (!this.attached || this.classifiedRevision != this.revision) {
            boolean empty = true, full = true;
            for (ItemStack stack : this) {
                if (stack.isEmpty()) full = false;
                else {
                    empty = false;
                    if (stack.getCount() != stack.getMaxStackSize()) full = false;
                }
            }
            this.fullState = empty ? 0 : full ? 2 : 1;
            this.classifiedRevision = this.revision;
        }
        return this.fullState;
    }

    @Override
    public void lithium$notify(ItemStack publisher, int slot) { this.changed(); }

    @Override
    public void lithium$notifyCount(ItemStack publisher, int slot, int newCount) { this.changed(); }

    @Override
    public void lithium$forceUnsubscribe(ItemStack publisher, int slot) {
        this.changed();
        this.recheckSubscriptions = true;
    }
}
